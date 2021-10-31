package dev.skomlach.biometric.compat.monetsupport.core

class MonetInstanceException :
    NullPointerException("Cannot access MonetCompat instance before calling create")

class MonetAttributeNotFoundException(attributeName: String) :
    Exception("Attribute $attributeName not set in your theme")