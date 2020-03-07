package com.documentscanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.shapes.PathShape;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import com.documentscanner.views.OpenNoteCameraView;
import com.documentscanner.helpers.OpenNoteMessage;
import com.documentscanner.helpers.PreviewFrame;
import com.documentscanner.helpers.Quadrilateral;
import com.documentscanner.helpers.ScannedDocument;
import com.documentscanner.helpers.Utils;
import com.documentscanner.views.HUDCanvasView;

import org.opencv.core.Core;
import org.opencv.core.Scalar;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.CLAHE;
import org.opencv.photo.Photo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;

public class ImageProcessor extends Handler {

    private static final String TAG = "ImageProcessor";
    private final OpenNoteCameraView mMainActivity;
    private boolean mBugRotate;
    private double colorGain = 1; // contrast
    private double colorBias = 10; // bright
    private Size mPreviewSize;
    private Point[] mPreviewPoints;
    private int numOfSquares = 0;
    private int numOfRectangles = 10;
    private double lastCaptureTime = 0;
    private double durationBetweenCaptures = 0;

    public ImageProcessor(Looper looper, OpenNoteCameraView mainActivity, Context context) {
        super(looper);
        this.mMainActivity = mainActivity;
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        mBugRotate = sharedPref.getBoolean("bug_rotate", false);
    }

    public void setNumOfRectangles(int numOfRectangles) {
        this.numOfRectangles = numOfRectangles;
    }

    public void setDurationBetweenCaptures(double durationBetweenCaptures) {
        this.durationBetweenCaptures = durationBetweenCaptures;
    }

    public void setBrightness(double brightness) {
        this.colorBias = brightness;
    }

    public void setContrast(double contrast) {
        this.colorGain = contrast;
    }

    public void handleMessage(Message msg) {

        if (msg.obj.getClass() == OpenNoteMessage.class) {

            OpenNoteMessage obj = (OpenNoteMessage) msg.obj;

            String command = obj.getCommand();

            Log.d(TAG, "Message Received: " + command + " - " + obj.getObj().toString());
            // TODO: Manage command.equals("colorMode" || "filterMode"), return boolean

            if (command.equals("previewFrame")) {
                processPreviewFrame((PreviewFrame) obj.getObj());
            } else if (command.equals("pictureTaken")) {
                processPicture((Mat) obj.getObj());
            }
        }
    }

    private void processPreviewFrame(PreviewFrame previewFrame) {

        Mat frame = previewFrame.getFrame();

        boolean focused = mMainActivity.isFocused();

        if (detectPreviewDocument(frame) && focused) {
            numOfSquares++;
            double now = (double)(new Date()).getTime() / 1000.0;
            if (numOfSquares == numOfRectangles && now > lastCaptureTime + durationBetweenCaptures) {
                lastCaptureTime = now;
                numOfSquares = 0;
                mMainActivity.requestPicture();
                mMainActivity.waitSpinnerVisible();
            }
        } else {
            numOfSquares = 0;
        }

        frame.release();
        mMainActivity.setImageProcessorBusy(false);

    }

    public void processPicture(Mat picture) {

        Mat img = Imgcodecs.imdecode(picture, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
        Mat originalMat = new Mat();
        img.copyTo(originalMat);
        picture.release();

        Log.d(TAG, "processPicture - imported image " + img.size().width + "x" + img.size().height);

        if (mBugRotate) {
            Core.flip(img, img, 1);
            Core.flip(img, img, 0);
        }

        ScannedDocument doc = detectDocument(img);

        mMainActivity.getHUD().clear();
        mMainActivity.invalidateHUD();
        mMainActivity.saveDocument(doc, originalMat);
        doc.release();
        picture.release();

        mMainActivity.setImageProcessorBusy(false);
        mMainActivity.waitSpinnerInvisible();

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
        if (quad != null) {

            sd.originalPoints = new Point[4];

            sd.originalPoints[0] = new Point(sd.widthWithRatio - quad.points[3].y, quad.points[3].x); // TopLeft
            sd.originalPoints[1] = new Point(sd.widthWithRatio - quad.points[0].y, quad.points[0].x); // TopRight
            sd.originalPoints[2] = new Point(sd.widthWithRatio - quad.points[1].y, quad.points[1].x); // BottomRight
            sd.originalPoints[3] = new Point(sd.widthWithRatio - quad.points[2].y, quad.points[2].x); // BottomLeft

            sd.quadrilateral = quad;
            sd.previewPoints = mPreviewPoints;
            sd.previewSize = mPreviewSize;

            doc = fourPointTransform(inputRgba, quad.points);
        } else {
            doc = new Mat(inputRgba.size(), CvType.CV_8UC4);
            inputRgba.copyTo(doc);
        }
        return sd.setProcessed(doc);
    }

    private final HashMap<String, Long> pageHistory = new HashMap<>();

    private boolean checkQR(String qrCode) {

        return !(pageHistory.containsKey(qrCode) && pageHistory.get(qrCode) > new Date().getTime() / 1000 - 15);

    }

    private boolean detectPreviewDocument(Mat inputRgba) {

        ArrayList<MatOfPoint> contours = findContours(inputRgba);

        Quadrilateral quad = getQuadrilateral(contours, inputRgba.size());

        Log.i("DESENHAR", "Quad----->" + quad);

        mPreviewPoints = null;
        mPreviewSize = inputRgba.size();

        if (quad != null) {

            Point[] rescaledPoints = new Point[4];

            double ratio = inputRgba.size().height / 500;

            for (int i = 0; i < 4; i++) {
                int x = Double.valueOf(quad.points[i].x * ratio).intValue();
                int y = Double.valueOf(quad.points[i].y * ratio).intValue();
                if (mBugRotate) {
                    rescaledPoints[(i + 2) % 4] = new Point(Math.abs(x - mPreviewSize.width),
                            Math.abs(y - mPreviewSize.height));
                } else {
                    rescaledPoints[i] = new Point(x, y);
                }
            }

            mPreviewPoints = rescaledPoints;

            drawDocumentBox(mPreviewPoints, mPreviewSize);

            return true;

        }

        mMainActivity.getHUD().clear();
        mMainActivity.invalidateHUD();

        return false;

    }

    private void drawDocumentBox(Point[] points, Size stdSize) {

        Path path = new Path();

        HUDCanvasView hud = mMainActivity.getHUD();

        // ATTENTION: axis are swapped

        float previewWidth = (float) stdSize.height;
        float previewHeight = (float) stdSize.width;

        path.moveTo(previewWidth - (float) points[0].y, (float) points[0].x);
        path.lineTo(previewWidth - (float) points[1].y, (float) points[1].x);
        path.lineTo(previewWidth - (float) points[2].y, (float) points[2].x);
        path.lineTo(previewWidth - (float) points[3].y, (float) points[3].x);
        path.close();

        PathShape newBox = new PathShape(path, previewWidth, previewHeight);

        Paint paint = new Paint();
        paint.setColor(mMainActivity.parsedOverlayColor());

        Paint border = new Paint();
        border.setColor(mMainActivity.parsedOverlayColor());
        border.setStrokeWidth(5);

        hud.clear();
        hud.addShape(newBox, paint, border);
        mMainActivity.invalidateHUD();

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

            if (insideArea(foundPoints, size)) {

                return new Quadrilateral(c, foundPoints);
            }
            // }
        }

        return null;
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

        return isANormalShape && isAnActualRectangle && isBigEnough;
    }

    private void enhanceDocument(Mat src) {
        Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2GRAY);
        //CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
        //clahe.apply(src, src);
        //Imgproc.applyColorMap(src, src, Imgproc.COLORMAP_HSV);
        src.convertTo(src, CvType.CV_8UC1, colorGain, colorBias);
        Imgproc.adaptiveThreshold(src, src, 255, 1, 0, 41, 7);
        Imgproc.morphologyEx(src, src, Imgproc.MORPH_OPEN, new Mat(1, 1, CvType.CV_8U, Scalar.all(1)));
        Imgproc.morphologyEx(src, src, Imgproc.MORPH_CLOSE, new Mat(1, 1, CvType.CV_8U, Scalar.all(1)));

        // smoothening
        /* Imgproc.threshold(src, src, 180, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(src, src, 0, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(src, src, 0, 255, Imgproc.THRESH_OTSU);
        Imgproc.GaussianBlur(src, src, new Size(1, 1), 0);
        Imgproc.threshold(src, src, 0, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(src, src, 0, 255, Imgproc.THRESH_OTSU); */
        
        //Photo.fastNlMeansDenoising(src, src, 3, 7, 21);
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
        //Imgproc.equalizeHist(grayImage, grayImage);
        //CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
        //clahe.apply(grayImage, grayImage);
        Imgproc.applyColorMap(grayImage, grayImage, Imgproc.COLORMAP_HSV);
        Imgproc.GaussianBlur(grayImage, grayImage, new Size(5, 5), 0);
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

    public void setBugRotate(boolean bugRotate) {
        mBugRotate = bugRotate;
    }

}
