import React, { Component } from 'react';
import {
    NativeModules,
    PanResponder,
    Dimensions,
    Image,
    View,
    Animated,
    Text
} from 'react-native';
import Svg, { Polygon } from 'react-native-svg';

const AnimatedPolygon = Animated.createAnimatedComponent(Polygon);

class CustomCrop extends Component {
    constructor(props) {
        super(props);
        this.state = {
            viewHeight:
                Dimensions.get('window').width * (props.height / props.width),
            height: props.height,
            width: props.width,
            image: props.initialImage,
            moving: false,
        };

        this.state = {
            ...this.state,
            topLeft: new Animated.ValueXY(
                props.rectangleCoordinates
                    ? this.imageCoordinatesToViewCoordinates(
                        props.rectangleCoordinates.topLeft,
                        true,
                    )
                    : { x: 100, y: 100 },
            ),
            topRight: new Animated.ValueXY(
                props.rectangleCoordinates
                    ? this.imageCoordinatesToViewCoordinates(
                        props.rectangleCoordinates.topRight,
                        true,
                    )
                    : { x: Dimensions.get('window').width - 100, y: 100 },
            ),
            bottomLeft: new Animated.ValueXY(
                props.rectangleCoordinates
                    ? this.imageCoordinatesToViewCoordinates(
                        props.rectangleCoordinates.bottomLeft,
                        true,
                    )
                    : { x: 100, y: this.state.viewHeight - 100 },
            ),
            bottomRight: new Animated.ValueXY(
                props.rectangleCoordinates
                    ? this.imageCoordinatesToViewCoordinates(
                        props.rectangleCoordinates.bottomRight,
                        true,
                    )
                    : {
                        x: Dimensions.get('window').width - 100,
                        y: this.state.viewHeight - 100,
                    },
            ),
        };
        this.state = {
            ...this.state,
            overlayPositions: `${this.state.topLeft.x._value},${
                this.state.topLeft.y._value
                } ${this.state.topRight.x._value},${this.state.topRight.y._value} ${
                this.state.bottomRight.x._value
                },${this.state.bottomRight.y._value} ${
                this.state.bottomLeft.x._value
                },${this.state.bottomLeft.y._value}`,
        };

        this.panResponderTopLeft = this.createPanResponser(this.state.topLeft, 'tl');
        this.panResponderTopRight = this.createPanResponser(this.state.topRight, 'tr');
        this.panResponderBottomLeft = this.createPanResponser(this.state.bottomLeft, 'bl');
        this.panResponderBottomRight = this.createPanResponser(this.state.bottomRight, 'br');

        Image.getSize(props.initialImage, (w, h) => {
            let ratio = w / h
            let windowHeight = Dimensions.get('window').height
            let windowWidth = Dimensions.get('window').width

            let tryHeight = (windowHeight / 100) * 70
            let tryWidth = ratio * tryHeight

            if (tryWidth > windowWidth - 80) {
                tryWidth = windowWidth - 80
                tryHeight = tryWidth / ratio
            }

            this.setState({
                imageHeight: h,
                imageWidth: w,
                containerHeight: tryHeight,
                containerWidth: tryWidth,
                height: tryHeight,
                width: tryWidth,
                viewHeight: tryHeight,
                windowHeight,
                windowWidth
            })

        }, (err) => console.log(err))
    }

    createPanResponser(corner, type) {
        return PanResponder.create({
            onStartShouldSetPanResponder: (e, gestureState) => true,
            onPanResponderMove: (e, gestureState) => {
                this.setState({})
                const { height, width } = this.state

                let windowHeight = Dimensions.get('window').height
                let windowWidth = Dimensions.get('window').width

                let viewImageDistanceX = (windowWidth / 2) - (width / 2)

                // BOTH X LESS AND Y LESS
                if ((corner.x._value + gestureState.dx) < viewImageDistanceX && (corner.y._value + gestureState.dy) < 0) {
                    corner.setOffset(({ x: -(corner.x._value - viewImageDistanceX), y: -corner.y._value }))
                }

                // X MORE AND Y LESS
                else if ((corner.x._value + gestureState.dx) > (viewImageDistanceX + width) && (corner.y._value + gestureState.dy) < 0) {
                    corner.setOffset(({ x: (width + viewImageDistanceX) - corner.x._value, y: -corner.y._value }))
                }

                // X LESS AND Y MORE
                else if ((corner.x._value + gestureState.dx) < viewImageDistanceX && (corner.y._value + gestureState.dy) > height) {
                    corner.setOffset(({ x: -(corner.x._value - viewImageDistanceX), y: (height - corner.y._value) }))
                }

                // BOTH X MORE AND Y MORE
                else if ((corner.x._value + gestureState.dx) > (viewImageDistanceX + width) && (corner.y._value + gestureState.dy) > height) {
                    corner.setOffset(({ x: (width + viewImageDistanceX) - corner.x._value, y: (height - corner.y._value) }))
                }

                // X LESS
                else if ((corner.x._value + gestureState.dx) < viewImageDistanceX) {
                    corner.setOffset(({ x: -(corner.x._value - viewImageDistanceX), y: gestureState.dy }))
                }

                // X MORE
                else if ((corner.x._value + gestureState.dx) > (viewImageDistanceX + width)) {
                    corner.setOffset(({ x: (width + viewImageDistanceX) - corner.x._value, y: gestureState.dy }))
                }

                // Y LESS
                else if ((corner.y._value + gestureState.dy) < 0) {
                    corner.setOffset(({ x: gestureState.dx, y: -corner.y._value }))
                }

                // Y MORE
                else if ((corner.y._value + gestureState.dy) > height) {
                    corner.setOffset(({ x: gestureState.dx, y: (height - corner.y._value) }))
                }
                else {
                    corner.setOffset(({ x: gestureState.dx, y: gestureState.dy }))
                }
            },
            onPanResponderRelease: () => {
                corner.flattenOffset();
                this.updateOverlayString();
            },
        });
    }

    crop() {
        const coordinates = {
            topLeft: this.viewCoordinatesToImageCoordinates(this.state.topLeft),
            topRight: this.viewCoordinatesToImageCoordinates(this.state.topRight),
            bottomLeft: this.viewCoordinatesToImageCoordinates(this.state.bottomLeft),
            bottomRight: this.viewCoordinatesToImageCoordinates(this.state.bottomRight),
            height: this.state.imageHeight,
            width: this.state.imageWidth,
        };
        NativeModules.CustomCropManager.crop(
            coordinates,
            this.state.image,
            (err, res) => this.props.updateImage(res.image, coordinates),
        );
    }

    updateOverlayString() {
        this.setState({
            overlayPositions: `${this.state.topLeft.x._value},${
                this.state.topLeft.y._value
                } ${this.state.topRight.x._value},${this.state.topRight.y._value} ${
                this.state.bottomRight.x._value
                },${this.state.bottomRight.y._value} ${
                this.state.bottomLeft.x._value
                },${this.state.bottomLeft.y._value}`,
        });
    }

    imageCoordinatesToViewCoordinates(corner) {
        return {
            x: (corner.x * Dimensions.get('window').width) / this.state.width,
            y: (corner.y * this.state.viewHeight) / this.state.height,
        };
    }

    viewCoordinatesToImageCoordinates(corner) {
        const { height, width, imageHeight, imageWidth } = this.state

        let windowHeight = Dimensions.get('window').height
        let windowWidth = Dimensions.get('window').width

        let viewImageDistanceX = (windowWidth / 2) - (width / 2)

        let X = corner.x._value - viewImageDistanceX
        let Y = corner.y._value

        let XViewPercentage = X / (width / 100)
        let YViewPercentage = Y / (height / 100)

        let XImage = (imageWidth / 100) * XViewPercentage
        let YImage = (imageHeight / 100) * YViewPercentage

        return {
            x: XImage,
            y: YImage
        };
    }

    render() {
        const { containerHeight } = this.state
        if (containerHeight) {
            return (
                <View
                    style={[
                        s(this.props).cropContainer,
                    ]}
                >
                    <Image
                        style={[
                            s(this.props).image,
                            { height: this.state.height, width: this.state.width },
                        ]}
                        resizeMode="cover"
                        source={{ uri: this.state.image }}
                    />
                    <Svg
                        height={this.state.viewHeight}
                        width={Dimensions.get('window').width}
                        style={{ position: 'absolute', left: 0, top: 0 }}
                    >
                        <AnimatedPolygon
                            ref={(ref) => (this.polygon = ref)}
                            fill={this.props.overlayColor || 'blue'}
                            fillOpacity={this.props.overlayOpacity || 0.5}
                            stroke={this.props.overlayStrokeColor || 'blue'}
                            points={this.state.overlayPositions}
                            strokeWidth={this.props.overlayStrokeWidth || 3}
                        />
                    </Svg>
                    <Animated.View
                        {...this.panResponderTopLeft.panHandlers}
                        style={[
                            this.state.topLeft.getLayout(),
                            s(this.props).handler,
                        ]}
                    >
                        <View
                            style={[
                                s(this.props).handlerI,
                                { left: -10, top: -10 },
                            ]}
                        />
                        <View
                            style={[
                                s(this.props).handlerRound,
                                { left: 31, top: 31 },
                            ]}
                        />
                    </Animated.View>
                    <Animated.View
                        {...this.panResponderTopRight.panHandlers}
                        style={[
                            this.state.topRight.getLayout(),
                            s(this.props).handler,
                        ]}
                    >
                        <View
                            style={[
                                s(this.props).handlerI,
                                { left: 10, top: -10 },
                            ]}
                        />
                        <View
                            style={[
                                s(this.props).handlerRound,
                                { right: 31, top: 31 },
                            ]}
                        />
                    </Animated.View>
                    <Animated.View
                        {...this.panResponderBottomLeft.panHandlers}
                        style={[
                            this.state.bottomLeft.getLayout(),
                            s(this.props).handler,
                        ]}
                    >
                        <View
                            style={[
                                s(this.props).handlerI,
                                { left: -10, top: 10 },
                            ]}
                        />
                        <View
                            style={[
                                s(this.props).handlerRound,
                                { left: 31, bottom: 31 },
                            ]}
                        />
                    </Animated.View>
                    <Animated.View
                        {...this.panResponderBottomRight.panHandlers}
                        style={[
                            this.state.bottomRight.getLayout(),
                            s(this.props).handler,
                        ]}
                    >
                        <View
                            style={[
                                s(this.props).handlerI,
                                { left: 10, top: 10 },
                            ]}
                        />
                        <View
                            style={[
                                s(this.props).handlerRound,
                                { right: 31, bottom: 31 },
                            ]}
                        />
                    </Animated.View>
                </View>
            );
        }
        return (
            <View />
        )
    }
}

const s = (props) => ({
    handlerI: {
        borderRadius: 0,
        height: 20,
        width: 20,
        backgroundColor: props.handlerColor || 'blue',
    },
    handlerRound: {
        width: 39,
        position: 'absolute',
        height: 39,
        borderRadius: 100,
        backgroundColor: props.handlerColor || 'blue',
    },
    bottomButton: {
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: 'blue',
        width: 70,
        height: 70,
        borderRadius: 100,
    },
    handler: {
        height: 140,
        width: 140,
        overflow: 'visible',
        marginLeft: -70,
        marginTop: -70,
        alignItems: 'center',
        justifyContent: 'center',
        position: 'absolute',
    },
    cropContainer: {
        alignItems: 'center',
        marginTop: '10%',
        height: '100%',
        width: Dimensions.get('window').width,
    },
});

export default CustomCrop;
