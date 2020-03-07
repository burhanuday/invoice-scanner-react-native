import React from 'react';
import { ViewStyle } from 'react-native';
export interface PictureTaken {
    rectangleCoordinates?: object;
    croppedImage: string;
    initialImage: string;
    width: number;
    height: number;
}
/**
 * TODO: Change to something like this
interface PictureTaken {
  uri: string;
  base64?: string;
  width?: number; // modify to get it
  height?: number; // modify to get it
  rectangleCoordinates?: object;
  initial: {
    uri: string;
    base64?: string;
    width: number; // modify to get it
    height: number; // modify to get it
  };
}
 */
interface PdfScannerProps {
    onPictureTaken?: (event: any) => void;
    onRectangleDetect?: (event: any) => void;
    onProcessing?: () => void;
    quality?: number;
    overlayColor?: number | string;
    enableTorch?: boolean;
    useFrontCam?: boolean;
    saturation?: number;
    brightness?: number;
    contrast?: number;
    detectionCountBeforeCapture?: number;
    durationBetweenCaptures?: number;
    detectionRefreshRateInMS?: number;
    documentAnimation?: boolean;
    noGrayScale?: boolean;
    manualOnly?: boolean;
    style?: ViewStyle;
    useBase64?: boolean;
    saveInAppDocument?: boolean;
    captureMultiple?: boolean;
}
declare class PdfScanner extends React.Component<PdfScannerProps> {
    sendOnPictureTakenEvent(event: any): void | null;
    sendOnRectangleDetectEvent(event: any): void | null;
    getImageQuality(): number;
    componentDidMount(): void;
    componentDidUpdate(prevProps: PdfScannerProps): void;
    componentWillUnmount(): void;
    capture(): void;
    _scannerRef: any;
    _scannerHandle: number | null;
    _setReference: (ref: any) => void;
    render(): JSX.Element;
}
export default PdfScanner;
