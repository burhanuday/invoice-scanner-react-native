/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 * @flow
 */

import React, {Component} from 'react';
import {
  SafeAreaView,
  StyleSheet,
  ScrollView,
  View,
  Text,
  StatusBar,
  Image,
  TouchableOpacity,
  Platform,
  Dimensions,
  NativeEventEmitter,
  NativeModules,
} from 'react-native';

import Permissions from 'react-native-permissions';
import CustomCrop from 'react-native-perspective-image-cropper/indexold';
import ImagePicker from 'react-native-image-picker';
import DocumentScanner, {
  onImagePicked,
} from '@woonivers/react-native-document-scanner';
import * as RNFS from 'react-native-fs';
import RNTextDetector from 'react-native-text-detector';
import ImageSize from 'react-native-image-size';
import ImageRotate from 'react-native-image-rotate';
import RNFetchBlob from 'rn-fetch-blob';

const SERVER_URL = 'http://172.16.100.47:5000';
const WHITE_BACKGROUND = '/white_background';
const WATERMARK = '/watermark';
const WHITE_BACKGROUND_URL = SERVER_URL + WHITE_BACKGROUND;
const WATERMARK_URL = SERVER_URL + WATERMARK;

class App extends Component {
  constructor(props) {
    super(props);
    this.myRef = React.createRef();
    this.myRef2 = React.createRef();
    this.state = {
      imageSelected: false,
      cropComplete: false,
      showCam: true,
      ocrText: '',
      flash: false,
      itemKey: 1,
    };
  }

  componentDidMount() {
    async function requestCamera() {
      const result = await Permissions.request(
        Platform.OS === 'android'
          ? 'android.permission.CAMERA'
          : 'ios.permission.CAMERA',
      );
      console.log('result', result);
    }
    requestCamera();

    const eventEmitter = new NativeEventEmitter(
      NativeModules.RNPdfScannerManager,
    );
    eventEmitter.addListener('ImagePickedEvent', data => {
      console.log('this was from listener');
      console.log(data);
      console.log('after data is print');

      if (data.apiCallRequired === 'y') {
        console.log('sending a request', WHITE_BACKGROUND_URL);
        RNFetchBlob.fetch(
          'POST',
          WHITE_BACKGROUND_URL,
          {
            'Content-Type': 'multipart/form-data',
          },
          [
            {
              name: 'file',
              filename: 'file.png',
              type: 'image/*',
              data: RNFetchBlob.wrap(data.unchangedFile),
            },
          ],
        )
          .then(response => {
            let status = response.info().status;
            console.log('response', response.json());

            if (status == 200) {
              // the conversion is done in native code
              let base64Str = response.base64();
              // the following conversions are done in js, it's SYNC
              let text = response.text();
              let json = response.json();
              //console.log('res', json, text);
            } else {
              // handle other status codes
            }

            const jsonRes = response.json();
            data.rectangleCoordinates.topRight = jsonRes.topRight;
            data.rectangleCoordinates.topLeft = jsonRes.topLeft;
            data.rectangleCoordinates.bottomLeft = jsonRes.bottomLeft;
            data.rectangleCoordinates.bottomRight = jsonRes.bottomRight;

            this.setState({
              imageWidth: jsonRes.width,
              imageHeight: jsonRes.height,
              image: data.initialImage,
              initialImage: data.initialImage,
              path: data.initialImage,
              rectangleCoordinates: data.rectangleCoordinates,
              showCam: false,
              imageSelected: true,
              unchangedFile: data.unchangedFile,
            });
          })
          // Something went wrong:
          .catch((errorMessage, statusCode) => {
            // error handling
            console.log('error', errorMessage, statusCode);
          });
      } else {
        ImageSize.getSize(data.initialImage).then(size => {
          const width = size.width;
          const height = size.height;
          console.log('size', width, height);
          if (
            Object.keys(data.rectangleCoordinates).length === 0 &&
            data.rectangleCoordinates.constructor === Object
          ) {
            data.rectangleCoordinates = {
              bottomLeft: {x: width / 10 + 50, y: height - 100},
              bottomRight: {x: width - width / 10, y: height - 100},
              topLeft: {x: width / 10 + 50, y: height / 10 + 50},
              topRight: {x: width - width / 10, y: height / 10 + 50},
            };
          } else {
            console.log('inside if');
            const windowWidth = Dimensions.get('window').width;
            const windowHeight = Dimensions.get('window').height;
            const topRight = data.rectangleCoordinates.topRight;
            const topLeft = data.rectangleCoordinates.topLeft;
            const bottomRight = data.rectangleCoordinates.bottomRight;
            const bottomLeft = data.rectangleCoordinates.bottomLeft;

            const ratio = width / 500;

            topRight['x'] = topRight['x'] * ratio;
            topRight['y'] = topRight['y'] * ratio;
            if (topRight['x'] > width) {
              topRight['x'] = width - 50;
            }
            if (topRight['y'] < 10) {
              topRight['y'] = 40;
            }
            data.rectangleCoordinates.topRight = topRight;

            topLeft['x'] = topLeft['x'] * ratio;
            topLeft['y'] = topLeft['y'] * ratio;
            if (topLeft['x'] < 10) {
              topLeft['x'] = 40;
            }
            if (topLeft['y'] < 10) {
              topLeft['y'] = 40;
            }
            data.rectangleCoordinates.topLeft = topLeft;

            bottomLeft['x'] = bottomLeft['x'] * ratio;
            bottomLeft['y'] = bottomLeft['y'] * ratio;
            if (bottomLeft['x'] < 10) {
              bottomLeft['x'] = 40;
            }
            if (bottomLeft['y'] > height) {
              bottomLeft['y'] = height - 60;
            }
            data.rectangleCoordinates.bottomLeft = bottomLeft;

            bottomRight['x'] = bottomRight['x'] * ratio;
            bottomRight['y'] = bottomRight['y'] * ratio;
            if (bottomRight['x'] > width) {
              bottomRight['x'] = width - 60;
            }
            if (bottomRight['y'] > height) {
              bottomRight['y'] = height - 60;
            }
            data.rectangleCoordinates.bottomRight = bottomRight;
          }
          console.log('dite', data.rectangleCoordinates);
          console.log('before settings state');
          this.setState({
            imageWidth: width,
            imageHeight: height,
            image: data.initialImage,
            initialImage: data.initialImage,
            path: data.initialImage,
            rectangleCoordinates: data.rectangleCoordinates,
            showCam: false,
            imageSelected: true,
            unchangedFile: data.unchangedFile,
          });
        });
      }
    });
  }

  detectText = async filePath => {
    try {
      const visionResp = await RNTextDetector.detectFromUri(filePath);
      /* visionResp.forEach(resp =>
        console.log('text:', resp.text, 'confidence score: ', resp.confidence),
      ); */
      console.log('visionResp', visionResp);
      const text = visionResp[0];
      if (text.length < 10) {
        this.setState({
          ocrText: 'No text detected',
        });
      } else {
        this.setState({
          ocrText: text,
        });
      }
    } catch (e) {
      console.log('error in ocr', e);
    }
  };

  updateImage(image, newCoordinates) {
    console.log('new', newCoordinates);
    //console.log('image', image);
    this.setState({
      image,
      rectangleCoordinates: newCoordinates,
      cropComplete: true,
    });
  }

  crop() {
    this.customCrop.crop();
  }

  selectImage() {
    ImagePicker.showImagePicker({title: 'Select'}, response => {
      //console.log('Response = ', response);

      if (response.didCancel) {
        console.log('User cancelled image picker');
      } else if (response.error) {
        console.log('ImagePicker Error: ', response.error);
      } else if (response.customButton) {
        console.log('User tapped custom button: ', response.customButton);
      } else {
        const image = response.uri;
        console.log('image', image);
        // You can also display the image using data:
        // const source = {uri: 'data:image/jpeg;base64,' + response.data};
        const source = {uri: response.data};
        onImagePicked(source.uri);
      }
    });
  }

  render() {
    if (this.state.cropComplete) {
      return (
        <>
          <ScrollView>
            <Image
              style={{width: Dimensions.get('window').width, height: 500}}
              source={{
                uri: `data:image/png;base64,${this.state.image}`,
              }}
              resizeMode="contain"
            />

            {this.state.ocrText ? (
              <Text
                style={{
                  paddingLeft: 15,
                }}>
                {this.state.ocrText}
              </Text>
            ) : null}

            <View style={{height: 60}} />
          </ScrollView>

          {!this.state.ocrText ? (
            <TouchableOpacity
              style={{
                alignSelf: 'center',
                position: 'absolute',
                bottom: 32,
                width: 160,
                height: 45,
                backgroundColor: 'lightseagreen',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 12,
              }}
              onPress={() => {
                const filePath = `${
                  RNFS.ExternalStorageDirectoryPath
                }/Documents/${new Date().toISOString()}.jpg`.replace(/:/g, '-');
                console.log(filePath);
                RNFS.writeFile(filePath, `${this.state.image}`, 'base64')
                  .then(res => {
                    console.log('after file is saved, do ocr');
                    this.detectText('file://' + filePath);
                  })
                  .catch(err => {
                    console.log(err.message, err.code);
                  });
              }}>
              <Text
                style={{
                  fontSize: 16,
                  fontWeight: 'bold',
                  textAlign: 'center',
                  color: 'white',
                  letterSpacing: 2,
                }}>
                OCR
              </Text>
            </TouchableOpacity>
          ) : null}
        </>
      );
    }

    if (this.state.showCam) {
      return (
        <>
          <DocumentScanner
            useBase64
            ref={this.myRef}
            style={{
              flex: 0,
              aspectRatio: 1,
            }}
            saveInAppDocument={false}
            onPictureTaken={data => {
              console.log(data);
              console.log('after data is print');

              if (data.apiCallRequired === 'y') {
                console.log('sending a request', WHITE_BACKGROUND_URL);
                RNFetchBlob.fetch(
                  'POST',
                  WHITE_BACKGROUND_URL,
                  {
                    'Content-Type': 'multipart/form-data',
                  },
                  [
                    {
                      name: 'file',
                      filename: 'file.png',
                      type: 'image/*',
                      data: RNFetchBlob.wrap(data.unchangedFile),
                    },
                  ],
                )
                  .then(response => {
                    let status = response.info().status;
                    console.log('response', response.json());

                    if (status == 200) {
                      // the conversion is done in native code
                      let base64Str = response.base64();
                      // the following conversions are done in js, it's SYNC
                      let text = response.text();
                      let json = response.json();
                      //console.log('res', json, text);
                    } else {
                      // handle other status codes
                    }

                    const jsonRes = response.json();
                    data.rectangleCoordinates.topRight = jsonRes.topRight;
                    data.rectangleCoordinates.topLeft = jsonRes.topLeft;
                    data.rectangleCoordinates.bottomLeft = jsonRes.bottomLeft;
                    data.rectangleCoordinates.bottomRight = jsonRes.bottomRight;

                    this.setState({
                      imageWidth: jsonRes.width,
                      imageHeight: jsonRes.height,
                      image: data.initialImage,
                      initialImage: data.initialImage,
                      path: data.initialImage,
                      rectangleCoordinates: data.rectangleCoordinates,
                      showCam: false,
                      imageSelected: true,
                      unchangedFile: data.unchangedFile,
                    });
                  })
                  // Something went wrong:
                  .catch((errorMessage, statusCode) => {
                    // error handling
                    console.log('error', errorMessage, statusCode);
                  });
              } else {
                ImageSize.getSize(data.initialImage).then(size => {
                  const width = size.width;
                  const height = size.height;
                  console.log('size', width, height);
                  if (
                    Object.keys(data.rectangleCoordinates).length === 0 &&
                    data.rectangleCoordinates.constructor === Object
                  ) {
                    data.rectangleCoordinates = {
                      bottomLeft: {x: width / 10 + 50, y: height - 100},
                      bottomRight: {x: width - width / 10, y: height - 100},
                      topLeft: {x: width / 10 + 50, y: height / 10 + 50},
                      topRight: {x: width - width / 10, y: height / 10 + 50},
                    };
                  } else {
                    console.log('inside if');
                    const topRight = data.rectangleCoordinates.topRight;
                    const topLeft = data.rectangleCoordinates.topLeft;
                    const bottomRight = data.rectangleCoordinates.bottomRight;
                    const bottomLeft = data.rectangleCoordinates.bottomLeft;

                    const ratio = width / 500;

                    topRight['x'] = topRight['x'] * ratio;
                    topRight['y'] = topRight['y'] * ratio;
                    if (topRight['x'] > width) {
                      topRight['x'] = width - 50;
                    }
                    if (topRight['y'] < 10) {
                      topRight['y'] = 40;
                    }
                    data.rectangleCoordinates.topRight = topRight;

                    topLeft['x'] = topLeft['x'] * ratio;
                    topLeft['y'] = topLeft['y'] * ratio;
                    if (topLeft['x'] < 10) {
                      topLeft['x'] = 40;
                    }
                    if (topLeft['y'] < 10) {
                      topLeft['y'] = 40;
                    }
                    data.rectangleCoordinates.topLeft = topLeft;

                    bottomLeft['x'] = bottomLeft['x'] * ratio;
                    bottomLeft['y'] = bottomLeft['y'] * ratio;
                    if (bottomLeft['x'] < 10) {
                      bottomLeft['x'] = 40;
                    }
                    if (bottomLeft['y'] > height) {
                      bottomLeft['y'] = height - 60;
                    }
                    data.rectangleCoordinates.bottomLeft = bottomLeft;

                    bottomRight['x'] = bottomRight['x'] * ratio;
                    bottomRight['y'] = bottomRight['y'] * ratio;
                    if (bottomRight['x'] > width) {
                      bottomRight['x'] = width - 60;
                    }
                    if (bottomRight['y'] > height) {
                      bottomRight['y'] = height - 60;
                    }
                    data.rectangleCoordinates.bottomRight = bottomRight;
                  }

                  this.setState({
                    imageWidth: width,
                    imageHeight: height,
                    image: data.initialImage,
                    initialImage: data.initialImage,
                    path: data.initialImage,
                    rectangleCoordinates: data.rectangleCoordinates,
                    showCam: false,
                    imageSelected: true,
                    unchangedFile: data.unchangedFile,
                  });
                });
              }
            }}
            overlayColor="rgba(20,190,210, 0.6)"
            enableTorch={this.state.flash}
            brightness={0.3}
            saturation={1}
            contrast={1.1}
            quality={1}
            onRectangleDetect={({stableCounter, lastDetectionType}) =>
              this.setState({stableCounter, lastDetectionType})
            }
            detectionCountBeforeCapture={15}
            detectionRefreshRateInMS={50}
            onPermissionsDenied={() => console.log('Permissions Denied')}
            saveOnDevice={true}
          />
          <Image
            source={{uri: `data:image/jpeg;base64,${this.state.image}`}}
            resizeMode="contain"
          />

          <TouchableOpacity
            onPress={() => this.setState({flash: !this.state.flash})}
            style={{
              alignSelf: 'center',
              position: 'absolute',
              bottom: 148,
              width: 160,
              height: 45,
              backgroundColor: 'lightseagreen',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 12,
            }}>
            <Text
              style={{
                fontSize: 16,
                fontWeight: 'bold',
                textAlign: 'center',
                color: 'white',
                letterSpacing: 2,
              }}>
              TORCH
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            onPress={() => this.myRef.current.capture()}
            style={{
              alignSelf: 'center',
              position: 'absolute',
              bottom: 90,
              width: 160,
              height: 45,
              backgroundColor: 'lightseagreen',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 12,
            }}>
            <Text
              style={{
                fontSize: 16,
                fontWeight: 'bold',
                textAlign: 'center',
                color: 'white',
                letterSpacing: 2,
              }}>
              CAPTURE
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            onPress={this.selectImage.bind(this)}
            style={{
              alignSelf: 'center',
              position: 'absolute',
              bottom: 32,
              width: 160,
              height: 45,
              backgroundColor: 'lightseagreen',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 12,
            }}>
            <Text
              style={{
                fontSize: 16,
                fontWeight: 'bold',
                textAlign: 'center',
                color: 'white',
                letterSpacing: 2,
              }}>
              PICK IMAGE
            </Text>
          </TouchableOpacity>
        </>
      );
    }

    return (
      <>
        {console.log('im value', this.state.imageSelected)}
        {this.state.imageSelected && (
          <>
            {console.log('rendered')}
            <View style={{height: 30}} />
            <CustomCrop
              //key={this.state.itemKey}
              style={{
                flex: 0,
                aspectRatio: 1,
                backgroundColor: 'red',
                border: '1px solid red',
              }}
              updateImage={this.updateImage.bind(this)}
              rectangleCoordinates={this.state.rectangleCoordinates}
              initialImage={this.state.initialImage}
              height={this.state.imageHeight}
              width={this.state.imageWidth}
              path={this.state.path}
              ref={ref => (this.customCrop = ref)}
              overlayColor="rgba(18,190,210, 1)"
              overlayStrokeColor="rgba(20,190,210, 1)"
              handlerColor="rgba(20,150,160, 1)"
              enablePanStrict={false}
            />
            <TouchableOpacity
              style={{
                alignSelf: 'center',
                position: 'absolute',
                bottom: 90,
                width: 160,
                height: 45,
                backgroundColor: 'lightseagreen',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 12,
              }}
              onPress={() => {
                console.log('to send this', this.state.unchangedFile);

                RNFetchBlob.fetch(
                  'POST',
                  WATERMARK_URL,
                  {
                    'Content-Type': 'multipart/form-data',
                  },
                  [
                    {
                      name: 'file',
                      filename: 'file.png',
                      type: 'image/*',
                      data: RNFetchBlob.wrap(this.state.unchangedFile),
                    },
                  ],
                )
                  .then(response => {
                    let status = response.info().status;
                    console.log('res', Object.keys(response));
                    //console.log('res', response.data);
                    //console.log('response', response.json());

                    /* if (status == 200) {
                      // the conversion is done in native code
                      let base64Str = response.base64();
                      // the following conversions are done in js, it's SYNC
                      let text = response.text();
                      let json = response.json();
                      //console.log('res', json, text);
                    } else {
                      // handle other status codes
                    }

                    const jsonRes = response.json(); */
                    const filePath = `${
                      RNFS.ExternalStorageDirectoryPath
                    }/Documents/${new Date().toISOString()}.jpg`.replace(
                      /:/g,
                      '-',
                    );
                    console.log(filePath);
                    RNFS.writeFile(filePath, `${response.data}`, 'base64')
                      .then(res => {
                        console.log('assaved');
                        this.setState({
                          initialImage: filePath,
                          itemKey: 10,
                        });
                        // this.myRef.forceUpdate();
                      })
                      .catch(err => {
                        console.log(err.message, err.code);
                      });
                  })
                  // Something went wrong:
                  .catch((errorMessage, statusCode) => {
                    // error handling
                    console.log('error', errorMessage, statusCode);
                  });
              }}>
              <Text
                style={{
                  fontSize: 16,
                  fontWeight: 'bold',
                  textAlign: 'center',
                  color: 'white',
                  letterSpacing: 2,
                }}>
                REMOVE WATERMARK
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={{
                alignSelf: 'center',
                position: 'absolute',
                bottom: 32,
                width: 160,
                height: 45,
                backgroundColor: 'lightseagreen',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 12,
              }}
              onPress={this.crop.bind(this)}>
              <Text
                style={{
                  fontSize: 16,
                  fontWeight: 'bold',
                  textAlign: 'center',
                  color: 'white',
                  letterSpacing: 2,
                }}>
                CROP
              </Text>
            </TouchableOpacity>
          </>
        )}
        {!this.state.imageSelected && (
          <View>
            <TouchableOpacity
              style={{
                alignSelf: 'center',
                position: 'absolute',
                bottom: 32,
                width: 160,
                height: 45,
                backgroundColor: 'lightseagreen',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 12,
              }}
              onPress={this.selectImage.bind(this)}>
              <Text>SELECT</Text>
            </TouchableOpacity>
          </View>
        )}
      </>
    );
  }
}

export default App;
