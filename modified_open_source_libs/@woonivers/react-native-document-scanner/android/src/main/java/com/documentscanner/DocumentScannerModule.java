package com.documentscanner;

import com.documentscanner.views.MainView;
import com.documentscanner.views.OpenNoteCameraView;
import com.documentscanner.helpers.OpenNoteMessage;
import com.documentscanner.helpers.PreviewFrame;
import com.documentscanner.helpers.Quadrilateral;
import com.documentscanner.helpers.ScannedDocument;
// import com.documentscanner.helpers.Utils;
import com.documentscanner.views.HUDCanvasView;
import com.documentscanner.ImageProcessor;
import com.documentscanner.ApiService;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.uimanager.NativeViewHierarchyManager;
import com.facebook.react.uimanager.UIBlock;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableNativeMap;

import android.util.Base64;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.HandlerThread;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.shapes.PathShape;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Scalar;
// import org.opencv.dnn.Net;
// import org.opencv.dnn.Dnn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.lang.Math;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import com.google.gson.Gson;

import static com.documentscanner.helpers.Utils.addImageToGallery;

public class DocumentScannerModule extends ReactContextBaseJavaModule {
    /*
     * private HandlerThread mImageThread; private ImageProcessor mImageProcessor;
     */
    private Context mContext;
    private ReactContext mReactContext;

    public DocumentScannerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.mContext = getReactApplicationContext();
        this.mReactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNPdfScannerManager";
    }

    @ReactMethod
    public void capture(final int viewTag) {
        final ReactApplicationContext context = getReactApplicationContext();
        UIManagerModule uiManager = context.getNativeModule(UIManagerModule.class);
        uiManager.addUIBlock(new UIBlock() {
            @Override
            public void execute(NativeViewHierarchyManager nativeViewHierarchyManager) {
                try {
                    MainView view = (MainView) nativeViewHierarchyManager.resolveView(viewTag);
                    view.capture();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @ReactMethod
    public void processPickedImage(String imageUri) {
        final ReactApplicationContext context = getReactApplicationContext();
        Toast.makeText(context, imageUri.substring(0, 10), Toast.LENGTH_SHORT).show();

        byte[] decodedString = Base64.decode(imageUri, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        Mat tmp = new Mat();
        Mat tmp2 = new Mat();
        Utils.bitmapToMat(bitmap, tmp);

        tmp.copyTo(tmp2);

        if (true) {
            Core.transpose(tmp, tmp);
            Core.flip(tmp, tmp, 0);
        }

        if (true) {
            Core.transpose(tmp2, tmp2);
            Core.flip(tmp2, tmp2, 0);
        }

        ScannedDocument doc = detectDocument(tmp);

        saveDocument(doc, tmp2);
    }

    public void saveDocument(ScannedDocument scannedDocument, Mat tmp2) {

        Mat doc = (scannedDocument.processed != null) ? scannedDocument.processed : scannedDocument.original;

        String initialFileName = this.saveToDirectory(scannedDocument.original);
        String unchangedMat = this.saveToDirectory(tmp2);

        WritableMap data = new WritableNativeMap();

        data.putInt("height", scannedDocument.heightWithRatio);
        data.putInt("width", scannedDocument.widthWithRatio);
        data.putString("initialImage", "file://" + initialFileName);
        data.putMap("rectangleCoordinates", scannedDocument.previewPointsAsHash());

        if(scannedDocument.isNetworkRequestNecessary()){
            data.putString("apiCallRequired", "y");
            data.putString("unchangedFile", unchangedMat);
            sendEvent(this.mReactContext, "ImagePickedEvent", data);
        }else {
            sendEvent(this.mReactContext, "ImagePickedEvent", data);
        }
    }

    private String saveToDirectory(Mat doc) {
        String fileName;
        String folderName = "documents";
        String folderDir = true ? Environment.getExternalStorageDirectory().toString()
                : this.mContext.getCacheDir().toString();
        File folder = new File(folderDir + "/" + folderName);
        if (!folder.exists()) {
            boolean result = folder.mkdirs();
            if (result)
                Log.d("TAG", "wrote: created folder " + folder.getPath());
            else
                Log.d("TAG", "Not possible to create folder"); // TODO: Manage this error better
        }
        fileName = folderDir + "/" + folderName + "/" + UUID.randomUUID() + ".jpg";

        Mat endDoc = new Mat(Double.valueOf(doc.size().width).intValue(), Double.valueOf(doc.size().height).intValue(),
                CvType.CV_8UC4);

        Core.flip(doc.t(), endDoc, 1);

        Imgcodecs.imwrite(fileName, endDoc);

        endDoc.release();

        return fileName;
    }

    private ScannedDocument detectDocument(Mat inputRgba) {
        ArrayList<MatOfPoint> contours = findContours(inputRgba);
        enhanceDocument(inputRgba);
        ScannedDocument sd = new ScannedDocument(inputRgba);

        sd.originalSize = inputRgba.size();
        Quadrilateral quad = getQuadrilateral(contours, sd.originalSize);

        double ratio = sd.originalSize.height / 500;
        sd.heightWithRatio = Double.valueOf(sd.originalSize.width / ratio).intValue();
        sd.widthWithRatio = Double.valueOf(sd.originalSize.height / ratio).intValue();

        Mat doc;

        WritableMap data = new WritableNativeMap();
        data.putString("quad", String.valueOf(quad));
        data.putString("og sze", String.valueOf(sd.originalSize.width));
        data.putString("og sz2", String.valueOf(sd.originalSize.height));
        data.putString("cont", contours.get(0).toString());

        if (quad != null) {

            sd.originalPoints = new Point[4];

            sd.originalPoints[0] = new Point(sd.widthWithRatio - quad.points[3].y, quad.points[3].x); // TopLeft
            sd.originalPoints[1] = new Point(sd.widthWithRatio - quad.points[0].y, quad.points[0].x); // TopRight
            sd.originalPoints[2] = new Point(sd.widthWithRatio - quad.points[1].y, quad.points[1].x); // BottomRight
            sd.originalPoints[3] = new Point(sd.widthWithRatio - quad.points[2].y, quad.points[2].x); // BottomLeft
            data.putString("testData1", String.valueOf(sd.widthWithRatio - quad.points[3].y));
            data.putString("testData2", String.valueOf(sd.widthWithRatio - quad.points[0].y));

            sd.quadrilateral = quad;
            // sd.previewPoints = mPreviewPoints;
            // sd.previewSize = mPreviewSize;

            doc = fourPointTransform(inputRgba, quad.points);
        } else {
            doc = new Mat(inputRgba.size(), CvType.CV_8UC4);
            inputRgba.copyTo(doc);
        }
        //sendEvent(this.mReactContext, "ImagePickedEvent", data);
        return sd.setProcessed(doc);
    }

    private Quadrilateral getQuadrilateral(ArrayList<MatOfPoint> contours, Size srcSize) {

        double ratio = srcSize.height / 500;
        int height = Double.valueOf(srcSize.height / ratio).intValue();
        int width = Double.valueOf(srcSize.width / ratio).intValue();
        Size size = new Size(width, height);

        Log.i("COUCOU", "Size----->" + size);
        for (MatOfPoint c : contours) {
            MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
            double peri = Imgproc.arcLength(c2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true);

            Point[] points = approx.toArray();

            // select biggest 4 angles polygon
            // if (points.length == 4) {
            Point[] foundPoints = sortPoints(points);

            // data.putDouble("pointfound", foundPoints[0].x);
            // data.putDouble("pointfound2", foundPoints[0].y);
            if (insideArea(foundPoints, size)) {
                WritableMap data = new WritableNativeMap();
                data.putString("pointfound2", "yup");
                //sendEvent(this.mReactContext, "ImagePickedEvent", data);
                return new Quadrilateral(c, foundPoints);
            }
            // }
        }

        return null;
    }

    private Mat fourPointTransform(Mat src, Point[] pts) {

        double ratio = src.size().height / 500;

        Point tl = pts[0];
        Point tr = pts[1];
        Point br = pts[2];
        Point bl = pts[3];

        double widthA = Math.sqrt(Math.pow(br.x - bl.x, 2) + Math.pow(br.y - bl.y, 2));
        double widthB = Math.sqrt(Math.pow(tr.x - tl.x, 2) + Math.pow(tr.y - tl.y, 2));

        double dw = Math.max(widthA, widthB) * ratio;
        int maxWidth = Double.valueOf(dw).intValue();

        double heightA = Math.sqrt(Math.pow(tr.x - br.x, 2) + Math.pow(tr.y - br.y, 2));
        double heightB = Math.sqrt(Math.pow(tl.x - bl.x, 2) + Math.pow(tl.y - bl.y, 2));

        double dh = Math.max(heightA, heightB) * ratio;
        int maxHeight = Double.valueOf(dh).intValue();

        Mat doc = new Mat(maxHeight, maxWidth, CvType.CV_8UC4);

        Mat src_mat = new Mat(4, 1, CvType.CV_32FC2);
        Mat dst_mat = new Mat(4, 1, CvType.CV_32FC2);

        src_mat.put(0, 0, tl.x * ratio, tl.y * ratio, tr.x * ratio, tr.y * ratio, br.x * ratio, br.y * ratio,
                bl.x * ratio, bl.y * ratio);
        dst_mat.put(0, 0, 0.0, 0.0, dw, 0.0, dw, dh, 0.0, dh);

        Mat m = Imgproc.getPerspectiveTransform(src_mat, dst_mat);

        Imgproc.warpPerspective(src, doc, m, doc.size());

        return doc;
    }

    private void enhanceDocument(Mat src) {
        /* Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.adaptiveThreshold(src, src, 255, 1, 0, 41, 9); */

        Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2GRAY);
        src.convertTo(src, CvType.CV_8UC1, 1, 10);
        //CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
        //clahe.apply(src, src);
        //Imgproc.applyColorMap(src, src, Imgproc.COLORMAP_HSV);
        src.convertTo(src, CvType.CV_8UC1, 1, 10);
        Imgproc.adaptiveThreshold(src, src, 255, 1, 0, 41, 7);
        Imgproc.morphologyEx(src, src, Imgproc.MORPH_OPEN, new Mat(1, 1, CvType.CV_8U, Scalar.all(1)));
        Imgproc.morphologyEx(src, src, Imgproc.MORPH_CLOSE, new Mat(1, 1, CvType.CV_8U, Scalar.all(1)));
    }

    private Point[] sortPoints(Point[] src) {

        ArrayList<Point> srcPoints = new ArrayList<>(Arrays.asList(src));

        Point[] result = { null, null, null, null };

        Comparator<Point> sumComparator = new Comparator<Point>() {
            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.compare(lhs.y + lhs.x, rhs.y + rhs.x);
            }
        };

        Comparator<Point> diffComparator = new Comparator<Point>() {

            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.compare(lhs.y - lhs.x, rhs.y - rhs.x);
            }
        };

        // top-left corner = minimal sum
        result[0] = Collections.min(srcPoints, sumComparator);

        // bottom-right corner = maximal sum
        result[2] = Collections.max(srcPoints, sumComparator);

        // top-right corner = minimal difference
        result[1] = Collections.min(srcPoints, diffComparator);

        // bottom-left corner = maximal difference
        result[3] = Collections.max(srcPoints, diffComparator);

        return result;
    }

    private boolean insideArea(Point[] rp, Size size) {

        int width = Double.valueOf(size.width).intValue();
        int height = Double.valueOf(size.height).intValue();

        int minimumSize = width / 10;

        boolean isANormalShape = rp[0].x != rp[1].x && rp[1].y != rp[0].y && rp[2].y != rp[3].y && rp[3].x != rp[2].x;
        boolean isBigEnough = ((rp[1].x - rp[0].x >= minimumSize) && (rp[2].x - rp[3].x >= minimumSize)
                && (rp[3].y - rp[0].y >= minimumSize) && (rp[2].y - rp[1].y >= minimumSize));

        double leftOffset = rp[0].x - rp[3].x;
        double rightOffset = rp[1].x - rp[2].x;
        double bottomOffset = rp[0].y - rp[1].y;
        double topOffset = rp[2].y - rp[3].y;

        boolean isAnActualRectangle = ((leftOffset <= minimumSize && leftOffset >= -minimumSize)
                && (rightOffset <= minimumSize && rightOffset >= -minimumSize)
                && (bottomOffset <= minimumSize && bottomOffset >= -minimumSize)
                && (topOffset <= minimumSize && topOffset >= -minimumSize));

        WritableMap data = new WritableNativeMap();
        data.putString("isnormal", String.valueOf(isANormalShape));
        data.putString("isAnActualRectangle", String.valueOf(isAnActualRectangle));
        data.putString("isBigEnough", String.valueOf(isBigEnough));
        data.putString("minSize", String.valueOf(minimumSize));
        //sendEvent(this.mReactContext, "ImagePickedEvent", data);

        return isANormalShape && true && isBigEnough;
    }

    private ArrayList<MatOfPoint> findContours(Mat src) {

        Mat grayImage;
        Mat cannedImage;
        Mat resizedImage;

        double ratio = src.size().height / 500;
        int height = Double.valueOf(src.size().height / ratio).intValue();
        int width = Double.valueOf(src.size().width / ratio).intValue();
        Size size = new Size(width, height);

        resizedImage = new Mat(size, CvType.CV_8UC4);
        grayImage = new Mat(size, CvType.CV_8UC4);
        cannedImage = new Mat(size, CvType.CV_8UC1);

        Imgproc.resize(src, resizedImage, size);
        Imgproc.cvtColor(resizedImage, grayImage, Imgproc.COLOR_RGBA2GRAY, 4);
        //src.convertTo(grayImage, CvType.CV_8UC1, 1, 10);
        Imgproc.equalizeHist(grayImage, grayImage);
        //CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
        //clahe.apply(grayImage, grayImage);
        //Imgproc.applyColorMap(grayImage, grayImage, Imgproc.COLORMAP_HSV);
        Imgproc.GaussianBlur(grayImage, grayImage, new Size(5, 5), 0);

        /* int top, bottom, left, right;
        int borderType = Core.BORDER_CONSTANT;

        top = (int) (0.05*grayImage.rows()); bottom = top;
        left = (int) (0.05*grayImage.cols()); right = left;

        Scalar value = new Scalar( 0, 0, 0);
        Core.copyMakeBorder( grayImage, grayImage, top, bottom, left, right, borderType, value); */
        Imgproc.Canny(grayImage, cannedImage, 80, 100, 3, false);
        //Imgproc.Canny(grayImage, cannedImage, 75, 200, 3, false);

        ArrayList<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.findContours(cannedImage, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        hierarchy.release();

        Collections.sort(contours, new Comparator<MatOfPoint>() {

            @Override
            public int compare(MatOfPoint lhs, MatOfPoint rhs) {
                return Double.compare(Imgproc.contourArea(rhs), Imgproc.contourArea(lhs));
            }
        });

        resizedImage.release();
        grayImage.release();
        cannedImage.release();

        return contours;
    }

    private void sendEvent(ReactContext reactContext, String eventName, WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }
}
