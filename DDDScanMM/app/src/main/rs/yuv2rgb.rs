#pragma version(1)
#pragma rs java_package_name(com.home.mm.dddscan)
#pragma rs_fp_relaxed

rs_allocation gInputAllocaton;

uchar4 __attribute__((kernel)) yuv2rgb(uchar4 anyPixel, uint32_t x, uint32_t y) {
    uchar Y = rsGetElementAtYuv_uchar_Y(gInputAllocaton, x, y);
    uchar U = rsGetElementAtYuv_uchar_U(gInputAllocaton, x, y);
    uchar V = rsGetElementAtYuv_uchar_V(gInputAllocaton, x, y);

    // Convert YUV to RGB, JFIF transform with fixed-point math
    // R = Y + 1.402 * (V - 128)
    // G = Y - 0.34414 * (U - 128) - 0.71414 * (V - 128)
    // B = Y + 1.772 * (U - 128)

    int4 rgb;
    rgb.r = Y + V * 1436 / 1024 - 179;
    rgb.g = Y - U * 46549 / 131072 + 44 - V * 93604 / 131072 + 91;
    rgb.b = Y + U * 1814 / 1024 - 227;
    rgb.a = 255;

    return convert_uchar4(clamp(rgb, 0, 255));
}

uchar4 __attribute__((kernel)) yuv2rgb_XXX(uchar4 anyPixel, uint32_t x, uint32_t y) {
// unfinished code...
   uint32_t maxX = rsAllocationGetDimX(gInputAllocaton);
   uint8_t *p0 = (uint8_t*)rsGetElementAt(gInputAllocaton, y, x);
   uint8_t *p1 = (uint8_t*)rsGetElementAt(gInputAllocaton, y+maxX, x);
   uint8_t *p2 = (uint8_t*)rsGetElementAt(gInputAllocaton, y+2*maxX, x);
   uint8_t Y = *(p2);
   uint8_t U = *(p1);
   uint8_t V = *(p2);

    // Convert YUV to RGB, JFIF transform with fixed-point math
    // R = Y + 1.402 * (V - 128)
    // G = Y - 0.34414 * (U - 128) - 0.71414 * (V - 128)
    // B = Y + 1.772 * (U - 128)

   int4 rgb;
   rgb.r = Y;  rgb.g = Y;    rgb.b = Y;    rgb.a = 255;
   return convert_uchar4(clamp(rgb, 0, 255));
   return rsYuvToRGBA_uchar4(Y, U, V);
}
