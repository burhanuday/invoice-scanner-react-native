# CodeSquad PS1
Solution for Problem Statement 1 for AIDL 2020 conducted by [@unifynd](https://github.com/unifynd) technologies.

## Problem
Given images of bills/invoices, the task was to perform the following 3 operations:
  * Edge detection, cropping, flattening, enhancement of cropped image and compression.
  * Extracting text from the processed image.
  * The confidence score for the image to text conversion.

## Development

1. Make sure you have `react-native cli` & the latest Android SDK installed on your system. To get started with React Native, [follow here](https://reactnative.dev/docs/getting-started)

2. To install **OpenCV for Android**, [see here](https://github.com/davidmigloz/go-bees/wiki/Setup-OpenCV-3.1.0-in-Android-Studio-2.2)

3. Clone the github repository and install the dependencies using `npm`
```bash
$ git clone https://github.com/burhanuday/codesquad-PS1
$ cd codesquad-PS1
$ npm install
```
4. Move the modified versions of the libraries from the `modified_open_source_libs` to the `node_modules` folder. Replace in destination when asked

5. Run development build (Android SDK and adb tools are required to be installed)
```bash
$ npx react-native run-android --no-jetifier
$ npx react-native run-ios
```

4. Run the `flask` server from the `flask-server` folder
```bash
$ python app.py
```

### For Mac
1. Follow the instructions mentioned on [Getting Started](https://reactnative.dev/docs/getting-started) on React Native documentation

2. Download the project zip from [here](https://drive.google.com/drive/folders/10gUGSfVYNPMP7aw4T8nfkrQkMXGxzV2m?usp=sharing)

3. Edit the `sdk.dir` statement with the SDK path in the `<extracted-folder>/android/local.properties` file, for your machine.

4. If getting this error `Could not compile settings file 'android\settings.gradle`.
First run `/usr/libexec/java_home -V` which will output something like the following:
```bash
Matching Java Virtual Machines (2):
    13.0.1, x86_64:	"Java SE 13.0.1"	/Library/Java/JavaVirtualMachines/jdk-13.0.1.jdk/Contents/Home
    1.8.0_242, x86_64:	"AdoptOpenJDK 8"	/Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home

/Library/Java/JavaVirtualMachines/jdk-13.0.1.jdk/Contents/Home

```
Pick the version you want to be the default (1.8.0_242 the version of AdoptOpenJDK 8) then:
```bash
export JAVA_HOME=`/usr/libexec/java_home -v 1.8.0_242`

```



5. Run the app with `npx react-native run-android --no-jetifier`
## Screens
<a href="https://imgbb.com/"><img width="200" src="https://i.ibb.co/Bft09zp/photo6278161801069832522.jpg" alt="photo6278161801069832522" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/M8GGbP7/photo6278311635298920670.jpg" width="200" alt="photo6278311635298920670" border="0" /></a> <a href="https://imgbb.com/"><img width="200" src="https://i.ibb.co/w0mvNRr/photo6278311635298920671.jpg" alt="photo6278311635298920671" border="0" /></a> <a href="https://imgbb.com/"><img src="https://i.ibb.co/vj4PH8m/photo6278311635298920672.jpg" width="200" alt="photo6278311635298920672" border="0" /></a> <a href="https://imgbb.com/"><img width="200" src="https://i.ibb.co/ph8dvfT/photo6278311635298920673.jpg" alt="photo6278311635298920673" border="0" /></a>

## Build
1. Create and then copy a keystore file to android/app
```bash
$ keytool -genkey -v -keystore mykeystore.keystore -alias mykeyalias -keyalg RSA -keysize 2048 -validity 10000
```
2. Setup your gradle variables in android/gradle.properties
```bash
MYAPP_RELEASE_STORE_FILE=mykeystore.keystore
MYAPP_RELEASE_KEY_ALIAS=mykeyalias
MYAPP_RELEASE_STORE_PASSWORD=*****
MYAPP_RELEASE_KEY_PASSWORD=*****
```

3. Add signing config to android/app/build.gradle
```bash
android {
signingConfigs {
release {
storeFile file(MYAPP_RELEASE_STORE_FILE)
storePassword MYAPP_RELEASE_STORE_PASSWORD
keyAlias MYAPP_RELEASE_KEY_ALIAS
keyPassword MYAPP_RELEASE_KEY_PASSWORD
}
}
buildTypes {
release {
signingConfig signingConfigs.release
}
}
}
```

4. Setup your gradle variables in android/gradle.properties
```bash
cd android && ./gradlew assembleRelease
```
Your APK will get generated at: android/app/build/outputs/apk/app-release.apk

## Credits
Special thanks to [react-native-document-scanner](https://github.com/Woonivers/react-native-document-scanner#readme) & [react-native-perspective-image-cropper](https://github.com/Michaelvilleneuve/react-native-perspective-image-cropper)

**NOTE: We are using heavily modified versions of both these libraries to support our usecase. You can find these modified libraries in the `modified_open_source_libs/`**
