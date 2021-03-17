package org.chickenhook.restrictionbypass.helpers

object Reflection {
    /**
     * Retrieve a member of the given object
     *
     * @param obj   containing the member
     * @param field the member name
     * @param <T>   the type of the member
     * @return the member
     * @throws NoSuchFieldException   when field was found
     * @throws IllegalAccessException when field was not accessible
    </T> */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun <T> getReflective(obj: Any, field: String): T? {
        return getReflective(obj, obj.javaClass, field)
    }

    /**
     * Retrieve a member of the given object
     *
     * @param obj   containing the member (can be null on static field)
     * @param cls   class of a super type
     * @param field the member name
     * @param <T>   the type of the member
     * @return the member
     * @throws NoSuchFieldException   when field was found
     * @throws IllegalAccessException when field was not accessible
    </T> */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun <T> getReflective(obj: Any?, cls: Class<*>, field: String): T? {
        val f = cls.getDeclaredField(field)
        f.isAccessible = true
        return f[obj] as T?
    }

    /**
     * Set a member of the given object
     *
     * @param obj   containing the member
     * @param field the member name
     * @param value the value to be set
     * @throws NoSuchFieldException   when field was found
     * @throws IllegalAccessException when field was not accessible
     */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun setReflective(obj: Any, field: String, value: Any?) {
        setReflective(obj, obj.javaClass, field, value)
    }

    /**
     * Set a member of the given object
     *
     * @param obj   containing the member
     * @param cls   super class of the obj
     * @param field the member name
     * @param value the value to be set
     * @throws NoSuchFieldException   when field was found
     * @throws IllegalAccessException when field was not accessible
     */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun setReflective(obj: Any?, cls: Class<*>, field: String, value: Any?) {
        val f = cls.getDeclaredField(field)
        f.isAccessible = true
        f[obj] = value
    }
}