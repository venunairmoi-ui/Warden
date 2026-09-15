# Add project-specific ProGuard rules here as needed once isMinifyEnabled = true
# for a release build. Left empty deliberately for the dogfooding phase
# (spec section 9) — minification isn't worth the debugging overhead until
# you're actually preparing a Play Store build (Sprint 7).

# PdfBox-Android (0.15.13, PdfDecryptor.kt): JPXFilter references the
# optional com.gemalto.jp2 JPEG2000 codec classes reflectively, and that
# library is a SEPARATE optional Gradle dependency this app never adds --
# PdfDecryptor only ever uses PdfBox-Android to unlock a password-protected
# PDF and strip its security, never to decode a JP2-encoded image inside
# one, so there's nothing here for R8 to actually keep. Rules below are
# exactly what R8 itself generated into
# app/build/outputs/mapping/release/missing_rules.txt on a real
# assembleRelease -- -dontwarn (not a real dependency) is the correct fix
# per PdfBox-Android's own known-issue guidance for this exact class pair.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
