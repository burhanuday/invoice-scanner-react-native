#import "IPDFCameraViewController.h"
#import <React/RCTViewManager.h>

@interface DocumentScannerView : IPDFCameraViewController <IPDFCameraViewControllerDelegate>

@property (nonatomic, copy) RCTBubblingEventBlock onPictureTaken;
@property (nonatomic, copy) RCTBubblingEventBlock onRectangleDetect;
@property (nonatomic, assign) NSInteger detectionCountBeforeCapture;
@property (nonatomic, assign) NSInteger stableCounter;
@property (nonatomic, assign) double durationBetweenCaptures;
@property (nonatomic, assign) double lastCaptureTime;
@property (nonatomic, assign) float quality;
@property (nonatomic, assign) BOOL useBase64;
@property (nonatomic, assign) BOOL captureMultiple;
@property (nonatomic, assign) BOOL saveInAppDocument;

- (void) capture;

@end
