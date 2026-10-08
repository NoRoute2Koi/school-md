{ pkgs ? import <nixpkgs> {
    config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    };
  }
}:

let
  androidComposition = pkgs.androidenv.composeAndroidPackages {
    cmdLineToolsVersion = "13.0";
    buildToolsVersions = [ "35.0.0" ];
    platformVersions = [ "35" ];
    abiVersions = [ "x86_64" ];
  };

  androidSdk = androidComposition.androidsdk;

in
pkgs.mkShell {
  name = "schoolmd-dev-shell";

  buildInputs = [
    androidSdk
    pkgs.jdk17
    pkgs.android-tools
  ];

  shellHook = ''
    export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
    export ANDROID_SDK_ROOT="${androidSdk}/libexec/android-sdk"
    export JAVA_HOME="${pkgs.jdk17}"
    export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

    # Automatically set or update local.properties for tools and IDEs
    if [ ! -f "local.properties" ] || ! grep -q "sdk.dir" "local.properties"; then
      echo "sdk.dir=$ANDROID_HOME" > local.properties
    fi

    echo "=========================================================="
    echo "  SchoolMD Android Development Shell (NixOS)"
    echo "=========================================================="
    echo "  Java:         $(java -version 2>&1 | head -n 1)"
    echo "  ADB:          $(which adb 2>/dev/null || echo 'not found')"
    echo "  ANDROID_HOME: $ANDROID_HOME"
    echo ""
    echo "  Useful commands:"
    echo "    ./gradlew assembleDebug      - build debug APK"
    echo "    ./gradlew testDebugUnitTest  - run unit tests"
    echo "    adb install -r app/build/outputs/apk/debug/app-debug.apk"
    echo "=========================================================="
  '';
}
