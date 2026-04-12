# Attribr SDK — ProGuard consumer rules
# These rules are applied to apps that include the Attribr AAR.

# Keep the public API surface so apps can call Attribr.initialize() etc. after minification.
-keep public class com.mrvltechnologies.attribr.Attribr { *; }
-keep public enum  com.mrvltechnologies.attribr.ConsentState { *; }
-keep public enum  com.mrvltechnologies.attribr.AttributionSource { *; }
-keep public class com.mrvltechnologies.attribr.AttribrConfiguration { *; }
