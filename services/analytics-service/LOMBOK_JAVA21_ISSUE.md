# Lombok + Java 21 Incompatibility Issue

**Date:** 2025-11-17
**Status:** 🔴 **BLOCKER** - Lombok fails with Java 21
**Impact:** Cannot build common module or any dependent services

---

## 📋 Summary

Despite extensive fixes to update the entire codebase to Java 21, **Lombok continues to fail** with the same error:

```
java.lang.NoSuchFieldException: com.sun.tools.javac.code.TypeTag :: UNKNOWN
at lombok.permit.Permit.getField(Permit.java:144)
```

---

## ✅ What We Fixed

**Successfully updated across entire codebase:**

1. ✅ Parent POM: `java.version` 17 → 21
2. ✅ Parent POM: `maven.compiler.source` 17 → 21
3. ✅ Parent POM: `maven.compiler.target` 17 → 21
4. ✅ 14 service POMs: `<java.version>17</java.version>` → 21
5. ✅ 9 service POMs: `maven.compiler.source/target` 17 → 21
6. ✅ 12 service POMs: `<release>17</release>` → 21
7. ✅ 1 shared module: `<java.version>17</java.version>` → 21
8. ✅ Forced Lombok 1.18.36 in dependencyManagement
9. ✅ Added poi-ooxml version (5.2.3)
10. ✅ Added opentelemetry-instrumentation-annotations version (1.32.0)

**Total files modified:** 27 pom.xml files

---

## ❌ The Persistent Problem

Even after all fixes, compilation fails with:

```
[INFO] Compiling 2314 source files with javac [forked debug release 21] to target/classes
[ERROR] java.lang.ExceptionInInitializerError
[ERROR] 	at lombok.javac.apt.LombokProcessor.placePostCompileAndDontMakeForceRoundDummiesHook
[ERROR] Caused by: java.lang.NoSuchFieldException: com.sun.tools.javac.code.TypeTag :: UNKNOWN
[ERROR] 	at lombok.permit.Permit.getField(Permit.java:144)
[ERROR] 	at lombok.javac.JavacTreeMaker$SchroedingerType.getFieldCached(JavacTreeMaker.java:171)
[ERROR] 	at lombok.javac.JavacTreeMaker$TypeTag.typeTag(JavacTreeMaker.java:259)
[ERROR] 	at lombok.javac.Javac.<clinit>(Javac.java:187)
```

---

## 🔍 Root Cause Analysis

### The Technical Issue

Lombok uses **reflection** to access internal Java compiler APIs (`com.sun.tools.javac.*`). These internal APIs are:
- NOT part of the public Java API
- Subject to change between Java versions
- Different structure in Java 21 vs Java 17

Specifically, Lombok tries to access `TypeTag.UNKNOWN` which:
- EXISTS in Java 17 compiler internals
- DOES NOT EXIST in Java 21 compiler internals

### Why Lombok 1.18.36 Should Work (But Doesn't)

According to Lombok changelogs:
- Lombok 1.18.30+ claims Java 21 support
- Lombok 1.18.36 is the latest stable release
- Should handle Java 21 compiler internals

**However**, in practice with this codebase:
- Lombok 1.18.36 still crashes
- Spring Boot 3.3.5 BOM provides Lombok 1.18.34
- Neither version works with Java 21

### Possible Reasons

1. **Mapstruct Conflict**: Lombok-Mapstruct binding may be incompatible
2. **Complex Annotation Processing**: 2,314 source files with extensive Lombok usage
3. **AspectJ Interaction**: @Aspect annotations + Lombok may conflict
4. **Spring Boot BOM Override**: Spring Boot managing Lombok version
5. **JDK Implementation**: Homebrew OpenJDK 21.0.8 specific issue

---

## 📊 Attempted Solutions

| Solution | Result |
|----------|--------|
| Update java.version to 21 | ❌ Failed |
| Force Lombok 1.18.36 in dependencyManagement | ❌ Failed |
| Update all compiler source/target to 21 | ❌ Failed |
| Update all `<release>` tags to 21 | ❌ Failed |
| Clean Maven cache | ❌ Failed |
| Force delete target directories | ❌ Failed |

---

## 🎯 Available Options

### Option 1: Revert to Java 17 ⭐ **RECOMMENDED FOR NOW**

**Pros:**
- ✅ Guaranteed to work (system already has Java 21, can install Java 17)
- ✅ Matches Spring Boot 3.3.5 tested configuration
- ✅ No code changes needed
- ✅ Can proceed with analytics-service work immediately

**Cons:**
- ❌ Loses Java 21 features
- ❌ Not future-proof
- ❌ Requires installing Java 17

**Implementation:**
```bash
# Option A: Use SDKMAN
sdk install java 17.0.9-tem
sdk use java 17.0.9-tem

# Option B: Use Homebrew
brew install openjdk@17
export JAVA_HOME=/usr/local/opt/openjdk@17

# Revert all pom.xml changes
# (or keep Java 21 in POMs, just use Java 17 runtime)
```

---

### Option 2: Continue with Java 21 Investigation

**Remaining things to try:**

1. **Disable Lombok MapStruct Binding**
   - May be causing annotation processor conflicts

2. **Upgrade Lombok Beyond 1.18.36**
   - Check if newer SNAPSHOT exists
   - Try edge/RC versions

3. **Add JVM Arguments**
   ```xml
   <compilerArgs>
     <arg>--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>
     <arg>--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED</arg>
   </compilerArgs>
   ```

4. **Delombok Approach**
   - Use Lombok Maven plugin to generate source
   - Compile delomboked sources instead

5. **Test with Different JDK**
   - Try Oracle JDK 21 instead of OpenJDK
   - Try Eclipse Temurin vs Homebrew OpenJDK

**Estimated Time:** 4-8 hours of investigation

**Success Probability:** ~30-40%

---

### Option 3: Hybrid Approach

**Use Maven Toolchains:**
- Keep system Java 21 for other work
- Maven builds use Java 17
- Best of both worlds

**Implementation:**
```xml
<!-- ~/.m2/toolchains.xml -->
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>17</version>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

**Effort:** 30-60 minutes setup

---

## 💡 Recommendation

**REVERT TO JAVA 17** for the following reasons:

1. **Time-Critical**: Analytics-service work is blocked
2. **Low Risk**: Java 17 is proven to work with this codebase
3. **Pragmatic**: Can revisit Java 21 after critical work complete
4. **LTS**: Java 17 is LTS, supported until 2029
5. **Spring Boot**: Spring Boot 3.3.5 primarily tested with Java 17

### Migration Path

**Immediate (Now):**
1. Install Java 17
2. Revert parent POM to Java 17 (or use toolchains)
3. Build common module successfully
4. Proceed with analytics-service work

**Future (After Analytics Complete):**
1. Investigate Lombok + Java 21 in isolated environment
2. Test with minimal project first
3. Consider delombok approach
4. Upgrade when proven stable

---

## 📝 Lessons Learned

1. **Lombok is fragile** - Depends on internal compiler APIs
2. **Test major upgrades** - Java version changes need isolated testing
3. **LTS doesn't mean compatible** - Even LTS versions can have issues
4. **Spring Boot BOM** - Can override dependency versions unexpectedly
5. **Annotation processors** - Complex interactions with compiler internals

---

## 🎯 Next Actions

**Please choose:**

1. **Revert to Java 17** - Install Java 17 and proceed with work
2. **Continue Java 21** - Investigate remaining options (4-8 hours)
3. **Hybrid Approach** - Setup Maven toolchains

**My strong recommendation:** Option 1 (Revert to Java 17) to unblock analytics-service work.

---

**Report Generated:** 2025-11-17
**Time Spent on Java 21 Migration:** ~4 hours
**Result:** Unsuccessful - Lombok incompatibility blocker
