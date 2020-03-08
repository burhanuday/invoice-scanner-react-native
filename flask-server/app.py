from flask import Flask, render_template, request
from flask import Flask,request,jsonify
from imutils.object_detection import non_max_suppression
import numpy as np
import argparse
import time
from flask import send_file
import cv2
# import jsonify

# ALLOWED_EXTENSIONS = set(['png', 'jpg', 'jpeg'])

app = Flask(__name__)

# function to check the file extension
# def allowed_file(filename):
#     return '.' in filename and \
#            filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS
net = cv2.dnn.readNet("E:/Users/NM/envs/cv/aidl/opencv-text-detection/opencv-text-detection/frozen_east_text_detection.pb")

@app.route('/white_background', methods=['POST'])
def upload_page():
    if request.method == 'POST':
        # check if there is a file in the request
        if 'file' not in request.files:
            return render_template('upload.html', msg='No file selected')
        file = request.files['file']
        # print("file")
        # # if no file is selected
        # if file.filename == '':
        #     return render_template('upload.html', msg='No file selected')

        if file:
            path = './Uploads/' + file.filename
            file.save(path)
            image = cv2.imread(path)
            # print(type(image))
            # orig=input()
            # print("load")
            # orig = image.copy()
            (H, W) = image.shape[:2]
            height = image.shape[0]
            width = image.shape[1]

            # set the new width and height and then determine the ratio in change
            # for both the width and height
            (newW, newH) = (3008,4032)
            # print("(new)")
            rW = W / float(newW)
            rH = H / float(newH)

            # resize the image and grab the new image dimensions
            image = cv2.resize(image, (newW, newH))
            # print("resize")
            (H, W) = image.shape[:2]

            # define the two output layer names for the EAST detector model that
            # we are interested -- the first is the output probabilities and the
            # second can be used to derive the bounding box coordinates of text
            layerNames = [
                "feature_fusion/Conv_7/Sigmoid",
                "feature_fusion/concat_3"]
            # print("layername")

            # load the pre-trained EAST text detector
            print("[INFO] loading EAST text detector...")
            # net = cv2.dnn.readNet("E:/Users/NM/envs/cv/aidl/opencv-text-detection/opencv-text-detection/frozen_east_text_detection.pb")

            # construct a blob from the image and then perform a forward pass of
            # the model to obtain the two output layer sets
            blob = cv2.dnn.blobFromImage(image, 1.0, (W, H),
                (123.68, 116.78, 103.94), swapRB=True, crop=False)
            # print("beforetime")
            start = time.time()
            net.setInput(blob)
            (scores, geometry) = net.forward(layerNames)
            end = time.time()
            # print("aftertime")

            # show timing information on text prediction
            print("[INFO] text detection took {:.6f} seconds".format(end - start))

            # grab the number of rows and columns from the scores volume, then
            # initialize our set of bounding box rectangles and corresponding
            # confidence scores
            (numRows, numCols) = scores.shape[2:4]
            rects = []
            confidences = []
            # print("conifdence")

            # loop over the number of rows
            for y in range(0, numRows):
                # extract the scores (probabilities), followed by the geometrical
                # data used to derive potential bounding box coordinates that
                # surround text
                scoresData = scores[0, 0, y]
                xData0 = geometry[0, 0, y]
                xData1 = geometry[0, 1, y]
                xData2 = geometry[0, 2, y]
                xData3 = geometry[0, 3, y]
                anglesData = geometry[0, 4, y]
                # print("makegeo")

                # loop over the number of columns
                for x in range(0, numCols):
                    # if our score does not have sufficient probability, ignore it
                    if scoresData[x] < 0.5:
                        continue
                    # print("score")

                    # compute the offset factor as our resulting feature maps will
                    # be 4x smaller than the input image
                    (offsetX, offsetY) = (x * 4.0, y * 4.0)
                    # print("offset")

                    # extract the rotation angle for the prediction and then
                    # compute the sin and cosine
                    angle = anglesData[x]
                    cos = np.cos(angle)
                    sin = np.sin(angle)
                    # print("sin")

                    # use the geometry volume to derive the width and height of
                    # the bounding box
                    h = xData0[x] + xData2[x]
                    w = xData1[x] + xData3[x]
                    # print("h,w")

                    # compute both the starting and ending (x, y)-coordinates for
                    # the text prediction bounding box
                    endX = int(offsetX + (cos * xData1[x]) + (sin * xData2[x]))
                    endY = int(offsetY - (sin * xData1[x]) + (cos * xData2[x]))
                    startX = int(endX - w)
                    startY = int(endY - h)
                    # print("start")

                    # add the bounding box coordinates and probability score to
                    # our respective lists
                    rects.append((startX, startY, endX, endY))
                    confidences.append(scoresData[x])
                    # print("confiappend")

            # apply non-maxima suppression to suppress weak, overlapping bounding
            # boxes
            boxes = non_max_suppression(np.array(rects), probs=confidences)
            # print("boxes")
            list1=[]
            list2=[]
            list3=[]
            list4=[]
            # loop over the bounding boxes
            for (startX, startY, endX, endY) in boxes:
                # scale the bounding box coordinates based on the respective
                # ratios
                startX = int(startX * rW)
                startY = int(startY * rH)
                endX = int(endX * rW)
                endY = int(endY * rH)
                list1.append(startX)
                list2.append(startY)
                list3.append(endX)
                list4.append(endY)
                # print("boxes")

                # draw the bounding box on the image
                # cv2.rectangle(orig, (startX, startY), (endX, endY), (0, 255, 0), 2)
            s1=min(list1)
            # print(s1)
            s2=min(list2)
            # print(s2)
            # print(list3)
            e1=max(list3)
            # print(e1)
            dic={}
            e2=max(list4)
            # print(e2)
            q1=(s1,s2)
            q2=(e1,s2)
            q3=(s1,e2)
            q4=(e1,e2)
            # print("min")
            dic={"topLeft":{"x":s1,"y":s2},"topRight":{"x":e1,"y":s2},"bottomLeft":{"x":s1,"y":e2},"bottomRight":{"x":e1,"y":e2}, "height": height, "width": width}
            #             return jsonify(dic)
            print(dic)
            # cv2.rectangle(orig, (s1,s2), (e1,e2), (0,0,255), 3)
            return jsonify(dic)
            # show the output image
            # cv2.imshow("Text_Detection", orig)
            # cv2.waitKey(0)
            # cv2.destroyAllWindows()
            # show the output image
@app.route('/watermark',methods=['POST'])
def water_mark():
# @app.route('/white_background', methods=['POST'])
# def upload_page():/
    if request.method == 'POST':
        # check if there is a file in the request
        if 'file' not in request.files:
            return render_template('upload.html', msg='No file selected')
        file = request.files['file']
        # print("file")
        # # if no file is selected
        # if file.filename == '':
        #     return render_template('upload.html', msg='No file selected')

        if file:
            path = './Uploadswater/' + file.filename
            file.save(path)
            image = cv2.imread(path)
            gr = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

            # Make a copy of the grayscale image
            bg = gr.copy()

            # Apply morphological transformations
            for i in range(5):
                kernel2 = cv2.getStructuringElement(cv2.MORPH_ELLIPSE,
                                                    (2 * i + 1, 2 * i + 1))
                bg = cv2.morphologyEx(bg, cv2.MORPH_CLOSE, kernel2)
                bg = cv2.morphologyEx(bg, cv2.MORPH_OPEN, kernel2)

            # Subtract the grayscale image from its processed copy
            dif = cv2.subtract(bg, gr)

            # Apply thresholding
            bw = cv2.threshold(dif, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)[1]
            dark = cv2.threshold(bg, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)[1]
            print(dark)
            # Extract pixels in the dark region
            darkpix = gr[np.where(dark > 0)]
            print(darkpix)

            # Threshold the dark region to get the darker pixels inside it
            darkpix = cv2.threshold(darkpix, 0, 255, cv2.THRESH_BINARY | cv2.THRESH_OTSU)[1]

            # Paste the extracted darker pixels in the watermark region
            bw[np.where(dark > 0)] = darkpix.T
            cv2.imwrite('final1.jpg', bw)

            return send_file('final1.jpg', mimetype='image/*') 

            # cv2.imwrite('final1.jpg', bw)
    
            # cv2.imshow("Text Detection", orig)
            # cv2.waitKey(0)
if __name__ == '__main__':
    app.run(host="0.0.0.0")
