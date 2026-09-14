package dev.skomlach.biometric.compat.impl.dialogs

import android.content.Context
import android.R.attr as Attr
import android.content.res.Configuration
import android.content.res.TypedArray
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.collection.LruCache
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.skomlach.biometric.compat.utils.CheckBiometricUI
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.ExecutorHelper
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipFile

/** Reads known resource structures, without loading/executing code from SystemUI. */
internal object SystemBiometricDialogResources {
    private data class Result(val style: NativeDialogStyle?)
    private val cache = LruCache<String, Result>(4)
    private val pending = HashSet<String>()
    private val lock = Any()
    private val completed = MutableLiveData<String>()
    val updates: LiveData<String> = completed
    private val discoveredNames = LruCache<String, List<String>>(4)

    // Application and Activity configurations may have different window tokens, even when their
    // resource qualifiers match. Such tokens must not defeat startup prefetch.
    @RequiresApi(Build.VERSION_CODES.N)
    private fun key(c: Configuration, displayMinDp: Int) = listOf(Build.FINGERPRINT, displayMinDp, c.densityDpi, c.fontScale,
        c.orientation, c.screenWidthDp, c.screenHeightDp, c.smallestScreenWidthDp,
        c.screenLayout, c.uiMode, c.locales.toLanguageTags(), c.colorMode,
        if (Build.VERSION.SDK_INT >= 31) c.fontWeightAdjustment else 0).joinToString("|")

    fun cached(context: Context): NativeDialogStyle? {
        if (Build.VERSION.SDK_INT < 28) return null
        val key = configurationKey(context)
        return synchronized(lock) { cache[key]?.style }
    }

    fun hasCachedResult(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        val key = configurationKey(context)
        return synchronized(lock) { cache[key] != null }
    }

    fun configurationKey(context: Context): String =
        if (Build.VERSION.SDK_INT >= 28) key(context.resources.configuration, displayMinDp(context)) else ""

    // Match SystemUI's active logical display, including the Fold's inner/outer displays.
    // Activity bounds alone misclassify a large display in split-screen as a compact phone.
    private fun displayMinDp(context: Context): Int = try {
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            (minOf(bounds.width(), bounds.height()) / context.resources.displayMetrics.density).toInt()
        } else context.resources.configuration.smallestScreenWidthDp
    } catch (_: Exception) { context.resources.configuration.smallestScreenWidthDp }

    fun warmUp(context: Context) {
        if (Build.VERSION.SDK_INT < 28) return
        val app = context.applicationContext ?: return
        val configuration = Configuration(context.resources.configuration)
        val displayMinDp = displayMinDp(context)
        val key = key(configuration, displayMinDp)
        synchronized(lock) {
            if (cache[key] != null || !pending.add(key)) return
        }
        ExecutorHelper.startOnBackground {
            val startedAt = SystemClock.uptimeMillis()
            val style = try {
                val provider = CheckBiometricUI.getBiometricUiPackage(app)
                val foreign = app.createPackageContext(provider, Context.CONTEXT_RESTRICTED)
                    .createConfigurationContext(configuration)
                Reader(foreign, provider, NativeDialogLayoutPolicy.twoPane(
                    configuration.orientation == Configuration.ORIENTATION_LANDSCAPE, displayMinDp
                )).read()
            } catch (error: Exception) {
                BiometricLoggerImpl.d { "NativeDialogStyle: unavailable (${error.javaClass.simpleName})" }
                null
            } catch (error: LinkageError) {
                BiometricLoggerImpl.d { "NativeDialogStyle: unavailable (${error.javaClass.simpleName})" }
                null
            }
            synchronized(lock) {
                cache.put(key, Result(style)) // Cache misses too; don't reopen APKs on every prompt.
                pending.remove(key)
            }
            completed.postValue(key)
            BiometricLoggerImpl.d {
                "NativeDialogStyle: source=${style?.source ?: "fallback"} durationMs=${SystemClock.uptimeMillis() - startedAt}"
            }
        }
    }

    internal data class Element(
        val tag: String,
        val width: Int?,
        val height: Int?,
        val layoutGravity: Int?,
        val text: NativeTextStyle,
        val minHeight: Int?,
        val maxWidth: Int?,
        val guideBegin: Int?,
        val guideEnd: Int?,
        val parent: Element?
    )
    internal data class Layout(val root: String, val elements: Map<String, Element>, val includes: Set<Int>)

    private class Reader(private val context: Context, private val pkg: String, private val twoPane: Boolean) {
        private val res = context.resources
        private fun reject(reason: String): NativeDialogStyle? {
            BiometricLoggerImpl.d { "NativeDialogStyle: unsupported $reason" }
            return null
        }
        private fun id(name: String, type: String) = res.getIdentifier(name, type, pkg)
        private fun dimension(name: String): Int? = id(name, "dimen").takeIf { it != 0 }?.let(res::getDimensionPixelSize)

        fun read(): NativeDialogStyle? {
            val modernName = if (twoPane) "biometric_prompt_two_pane_layout" else "biometric_prompt_one_pane_layout"
            attempt { modern(modernName) }?.let { return it }
            // Prefer the active OEM family to bundled AOSP compatibility resources.
            attempt { auth() }?.let { return it }
            for (name in listOf("biometric_dialog", "fingerprint_dialog")) {
                attempt { oldDialog(name) }?.let { return it }
            }
            val candidates = discover().filter {
                it != modernName && NativeDialogLayoutPolicy.paneHint(it)?.let { pane -> pane == twoPane } != false
            }.mapNotNull { name -> attempt { modern(name) ?: oldDialog(name) } }
            return NativeDialogLayoutPolicy.unique(candidates)
        }

        private fun attempt(read: () -> NativeDialogStyle?): NativeDialogStyle? = try { read() }
        catch (_: Exception) { null } catch (_: LinkageError) { null }

        private fun valid(style: NativeDialogStyle): NativeDialogStyle? {
            val dm = res.displayMetrics
            return style.takeIf { it.isValid(dm.density, dm.scaledDensity) }
        }

        private fun modern(name: String): NativeDialogStyle? {
            val layout = layout(name) ?: return null
            if (!layout.root.endsWith(".ConstraintLayout")) return null
            val nodes = layout.elements
            if (!NativeDialogLayoutPolicy.matchesPane(name, twoPane, "midGuideline" in nodes)) return null
            val panel = nodes["panel"]?.takeIf { it.tag == "View" && it.width == 0 && it.height == 0 } ?: return null
            val scroll = nodes["scrollView"]?.takeIf { it.tag == "ScrollView" && it.width == 0 } ?: return null
            val logo = nodes["logo"]?.takeIf { it.tag == "ImageView" } ?: return null
            val logoDescription = nodes["logo_description"]?.takeIf { it.isText() } ?: return null
            val title = nodes["title"]?.takeIf { it.isText() } ?: return null
            val subtitle = nodes["subtitle"]?.takeIf { it.isText() } ?: return null
            val description = nodes["description"]?.takeIf { it.isText() } ?: return null
            val indicator = nodes["indicator"]?.takeIf { it.isText() } ?: return null
            if (nodes["biometric_icon"]?.tag?.endsWith("BiometricPromptLottieViewWrapper") != true) return null
            val button = layout.includes.mapNotNull { included ->
                layout(res.getResourceEntryName(included))?.elements?.get("button_negative")?.takeIf { it.isButton() }
            }.singleOrNull() ?: return null
            val left = nodes["leftGuideline"]?.guideBegin ?: return null
            val right = nodes["rightGuideline"]?.guideEnd ?: return null
            // One-pane uses an explicit maximum; two-pane intentionally has no tablet width cap.
            if (!twoPane && panel.maxWidth == null) return null
            return valid(NativeDialogStyle(
                source = "$pkg/$name", border = left,
                corner = dimension("biometric_dialog_corner_size") ?: return null,
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, contentWidth = panel.maxWidth,
                title = title.text, subtitle = subtitle.text, description = description.text,
                button = button.text, indicator = indicator.text,
                iconWidth = dimension("biometric_dialog_fingerprint_icon_width") ?: return null,
                iconHeight = dimension("biometric_dialog_fingerprint_icon_height") ?: return null,
                iconTop = 0, iconBottom = 0, centeredButton = false,
                outerInsets = NativeInsets(left, left, right, right),
                modern = NativeDialogModernStyle(twoPane, scroll.text.padding, panel.text.padding,
                    logo.width ?: return null, logo.height ?: return null, logoDescription.text,
                    button.minHeight)
            ))
        }

        private fun Element.isText() = tag == "TextView" || tag.endsWith(".TextView")
        private fun Element.isButton() = tag == "Button" || tag.endsWith(".Button")

        private fun oldDialog(name: String): NativeDialogStyle? {
            val layout = layout(name) ?: return null
            return NativeDialogLegacyStyle.read(pkg, name, layout, ::dimension)?.let(::valid)
        }

        /** Enumerate public APK paths only; no hidden resource table API or foreign code loading. */
        private fun discover(): List<String> {
            val info = context.applicationInfo
            val paths = (listOfNotNull(info.publicSourceDir) + info.splitPublicSourceDirs.orEmpty()).distinct()
            if (paths.size > 16) return emptyList()
            val key = paths.joinToString("|")
            synchronized(lock) { discoveredNames[key]?.let { return it } }
            val names = linkedSetOf<String>()
            var entriesRead = 0
            for (path in paths) ZipFile(path).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    if (++entriesRead > 100_000) return emptyList()
                    val pathName = entries.nextElement().name
                    val parts = pathName.split('/')
                    if (parts.size != 3 || parts[0] != "res" ||
                        (parts[1] != "layout" && !parts[1].startsWith("layout-")) || !parts[2].endsWith(".xml")) continue
                    val name = parts[2].removeSuffix(".xml")
                    if (NativeDialogLayoutPolicy.isDiscoveryCandidate(name)) names.add(name)
                    if (names.size > 64) return emptyList()
                }
            }
            return names.sorted().also { synchronized(lock) { discoveredNames.put(key, it) } }
        }

        private fun auth(): NativeDialogStyle? {
            // Prefer the OEM structure. Its bundled AOSP layouts may be unused compatibility resources.
            val oplus = id("oplus_auth_container_view", "layout") != 0
            val prefix = if (oplus) "oplus_" else ""
            val container = layout("${prefix}auth_container_view") ?: return reject("container")
            if (!NativeDialogLayoutPolicy.matchesAuthContainer(container.root, oplus)) return reject("container=${container.root}")
            val fingerprint = layout("${prefix}auth_biometric_fingerprint_view") ?: return reject("fingerprint layout")
            val expectedFingerprint = if (oplus) "com.oplus.systemui.biometrics.OplusAuthBiometricFingerprintView"
                else "com.android.systemui.biometrics.AuthBiometricFingerprintView"
            if (fingerprint.root != expectedFingerprint) return reject("fingerprint=${fingerprint.root}")
            val contentsId = id("${prefix}auth_biometric_contents", "layout")
            if (contentsId == 0 || contentsId !in fingerprint.includes) return reject("contents include")
            val contents = layout("${prefix}auth_biometric_contents") ?: return reject("contents layout")
            if (contents.root != "merge") return reject("contents=${contents.root}")
            val scroll = container.elements["biometric_scrollview"] ?: return reject("scroll view")
            if (scroll.tag != "ScrollView" || scroll.height != ViewGroup.LayoutParams.WRAP_CONTENT) return reject("scroll=$scroll")
            if (scroll.width == null || scroll.width == 0 || scroll.width < ViewGroup.LayoutParams.WRAP_CONTENT) return reject("width=${scroll.width}")
            if (container.root == "FrameLayout") {
                val root = scroll.parent ?: return reject("scroll parent")
                val panel = container.elements["panel"] ?: return reject("panel")
                val background = container.elements["background"] ?: return reject("background")
                if (root.parent != null || root.tag != "FrameLayout" || root.width != -1 || root.height != -1 ||
                    panel.tag != "View" || panel.parent !== root || panel.width != -1 || panel.height != -1 ||
                    background.tag != "ImageView" || background.parent !== root ||
                    background.width != -1 || background.height != -1) return reject("frame container structure")
            }
            val nodes = contents.elements
            val title = nodes["title"] ?: return null
            val subtitle = nodes["subtitle"] ?: return null
            val description = nodes["description"] ?: return null
            val button = nodes["button_negative"] ?: return null
            val icon = nodes["biometric_icon"] ?: return null
            val bar = nodes[if (oplus) "oplus_button_bar" else "button_bar"] ?: return null
            if (bar.tag != "LinearLayout") return null
            if (container.root == "FrameLayout" &&
                (!title.isText() || !subtitle.isText() || !description.isText() ||
                    !button.isButton() || button.parent !== bar ||
                    icon.tag !in setOf("ImageView", "com.airbnb.lottie.LottieAnimationView"))) {
                return reject("frame contents structure")
            }
            // A fixed width/height resource with a plausible name is not proof of its use.
            // These container classes measure the content within the scroll view's margins.
            val margins = scroll.text.margin
            if (margins.start != margins.end || margins.start != margins.top || margins.top != margins.bottom) return reject("margins=$margins")
            val style = NativeDialogStyle(
                source = "$pkg/${prefix}auth_biometric_contents",
                border = margins.start,
                corner = dimension("${prefix}biometric_dialog_corner_size") ?: return null,
                gravity = scroll.layoutGravity ?: return reject("missing layout_gravity"),
                contentWidth = scroll.width?.takeIf { it > 0 },
                title = title.text, subtitle = subtitle.text, description = description.text,
                button = button.text,
                indicator = nodes["indicator"]?.text,
                iconWidth = icon.width?.takeIf { it > 0 },
                iconHeight = icon.height?.takeIf { it > 0 },
                iconTop = icon.text.padding.top + (nodes["space_above_icon"]?.height?.coerceAtLeast(0) ?: 0),
                iconBottom = icon.text.padding.bottom + (nodes["space_below_icon"]?.height?.coerceAtLeast(0) ?: 0),
                centeredButton = oplus && button.text.gravity == Gravity.CENTER,
                buttonBarHeight = bar.height?.takeIf { it > 0 },
                buttonBarPadding = bar.text.padding
            )
            val dm = res.displayMetrics
            return if (style.isValid(dm.density, dm.scaledDensity)) style else reject("values=$style density=${dm.density}/${dm.scaledDensity}")
        }

        private fun layout(name: String): Layout? {
            val resourceId = id(name, "layout").takeIf { it != 0 } ?: return null
            val elements = LinkedHashMap<String, Element>()
            val includes = HashSet<Int>()
            var root: String? = null
            var count = 0
            val parents = HashMap<Int, Element>()
            res.getLayout(resourceId).use { xml ->
                while (xml.next() != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType != XmlPullParser.START_TAG) continue
                    if (++count > 128) return null
                    if (root == null) root = xml.name
                    if (xml.name == "include") includes.add(xml.getAttributeResourceValue(null, "layout", 0))
                    val element = element(xml.name, xml, parents[xml.depth - 1])
                    parents[xml.depth] = element
                    val viewId = xml.getAttributeResourceValue(ANDROID_NS, "id", 0)
                    if (viewId != 0) elements[res.getResourceEntryName(viewId)] = element
                }
            }
            return root?.let { Layout(it, elements, includes) }
        }

        private fun element(tag: String, xml: AttributeSet, parent: Element?): Element {
            val a = context.obtainStyledAttributes(xml, ATTRS, 0, 0)
            val appearanceId = a.getResourceId(index(Attr.textAppearance), 0)
            val appearance = appearanceId.takeIf { it != 0 }?.let { context.obtainStyledAttributes(it, ATTRS) }
            try {
                fun value(attr: Int) = when {
                    a.hasValue(index(attr)) -> a
                    appearance?.hasValue(index(attr)) == true -> appearance
                    else -> null
                }
                fun dimension(attr: Int) = value(attr)?.getDimensionPixelSize(index(attr), 0)
                fun int(attr: Int) = value(attr)?.getInt(index(attr), 0)
                fun bool(attr: Int) = value(attr)?.getBoolean(index(attr), false)
                val padding = dimension(Attr.padding) ?: 0
                val horizontal = dimension(Attr.paddingHorizontal) ?: padding
                val vertical = dimension(Attr.paddingVertical) ?: padding
                val margin = dimension(Attr.layout_margin) ?: 0
                val marginHorizontal = dimension(Attr.layout_marginHorizontal) ?: margin
                val marginVertical = dimension(Attr.layout_marginVertical) ?: margin
                fun customDimension(name: String): Int? {
                    val attrId = id(name, "attr").takeIf { it != 0 } ?: return null
                    val attributes = context.obtainStyledAttributes(xml, intArrayOf(attrId), 0, 0)
                    return try { if (attributes.hasValue(0)) attributes.getDimensionPixelSize(0, 0) else null }
                    finally { attributes.recycle() }
                }
                return Element(tag, a.optionalLayoutDimension(Attr.layout_width), a.optionalLayoutDimension(Attr.layout_height), int(Attr.layout_gravity),
                    NativeTextStyle(
                        size = value(Attr.textSize)?.getDimension(index(Attr.textSize), 0f), gravity = int(Attr.gravity),
                        padding = NativeInsets(dimension(Attr.paddingStart) ?: dimension(Attr.paddingLeft) ?: horizontal,
                            dimension(Attr.paddingTop) ?: vertical, dimension(Attr.paddingEnd) ?: dimension(Attr.paddingRight) ?: horizontal,
                            dimension(Attr.paddingBottom) ?: vertical),
                        margin = NativeInsets(dimension(Attr.layout_marginStart) ?: dimension(Attr.layout_marginLeft) ?: marginHorizontal,
                            dimension(Attr.layout_marginTop) ?: marginVertical, dimension(Attr.layout_marginEnd) ?: dimension(Attr.layout_marginRight) ?: marginHorizontal,
                            dimension(Attr.layout_marginBottom) ?: marginVertical),
                        includeFontPadding = bool(Attr.includeFontPadding), allCaps = bool(Attr.textAllCaps), textStyle = int(Attr.textStyle)
                    ), dimension(Attr.minHeight), customDimension("layout_constraintWidth_max"),
                    customDimension("layout_constraintGuide_begin"), customDimension("layout_constraintGuide_end"), parent)
            } finally {
                appearance?.recycle()
                a.recycle()
            }
        }
    }

    private fun index(attr: Int) = ATTRS.binarySearch(attr)

    private fun TypedArray.optionalLayoutDimension(attr: Int): Int? =
        if (hasValue(index(attr))) getLayoutDimension(index(attr), 0) else null

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private val ATTRS = intArrayOf(
        android.R.attr.textAppearance, android.R.attr.layout_width, android.R.attr.layout_height,
        android.R.attr.layout_gravity, android.R.attr.textSize, android.R.attr.gravity,
        android.R.attr.padding, android.R.attr.paddingLeft, android.R.attr.paddingTop,
        android.R.attr.paddingRight, android.R.attr.paddingBottom, android.R.attr.layout_margin,
        android.R.attr.layout_marginLeft, android.R.attr.layout_marginTop, android.R.attr.layout_marginRight,
        android.R.attr.layout_marginBottom, android.R.attr.includeFontPadding,
        android.R.attr.paddingHorizontal, android.R.attr.paddingVertical,
        android.R.attr.paddingStart, android.R.attr.paddingEnd,
        android.R.attr.layout_marginStart, android.R.attr.layout_marginEnd,
        android.R.attr.layout_marginHorizontal, android.R.attr.layout_marginVertical, android.R.attr.minHeight,
        android.R.attr.textAllCaps, android.R.attr.textStyle
    ).sortedArray() // obtainStyledAttributes requires ascending resource IDs, not declaration order.
}
