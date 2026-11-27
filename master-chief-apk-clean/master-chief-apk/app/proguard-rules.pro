# Add rules here if R8 strips something the app needs at runtime.
# The Gemini client uses kotlinx-serialization (reflection-free), and the
# local brain is plain Kotlin, so no extra keep rules are needed yet.
-keep class com.masterchief.agent.data.** { *; }
