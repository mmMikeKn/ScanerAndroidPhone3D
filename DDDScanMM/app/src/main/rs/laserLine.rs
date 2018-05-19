#pragma version(1)
#pragma rs java_package_name(com.home.mm.dddscan)
#pragma rs_fp_relaxed

rs_allocation gInputFrame;
rs_allocation gRedLaserPoints;
rs_allocation gGreenLaserPoints;

int probeWidth;
int thresholdLevel;

uint32_t histogramProbeLine;
static int histogramStart;

// tangentScreenW = tangentScreenCoefficientPerPixel * (sreenPosW - screenWidth / 2);
double tangentScreenCoefficientPerPixel;

static int screenHeight, screenWidth, halfScreenWidth, halfScreenHeight;

void setupInputFrames(rs_allocation inputFrame, double screenAngleTangent) {
 gInputFrame = inputFrame;
 screenHeight = rsAllocationGetDimY(gInputFrame);
 screenWidth = rsAllocationGetDimX(gInputFrame);
 tangentScreenCoefficientPerPixel = (screenAngleTangent*2)/screenWidth;
 halfScreenWidth = screenWidth/2;
 halfScreenHeight = screenHeight/2;

 histogramProbeLine = screenHeight/2;
 histogramStart = histogramProbeLine+200;
// rsDebug("(screenAngleTangent, tangentScreenCoefficientPerPixel, screenWidth)", screenAngleCoefficientW, tangentScreenCoefficientPerPixel, screenWidth);
}

void setupHistogram(uint32_t probeLine) {
 histogramProbeLine = probeLine;
 histogramStart = histogramProbeLine+200;
}

uchar4 __attribute__((kernel)) subImage(uchar4 in, uint32_t x, uint32_t y) {
 float4 a = rsUnpackColor8888(in);
 float4 b = rsUnpackColor8888(rsGetElementAt_uchar4(gInputFrame, x, y));
 return rsPackColorTo8888(a-b);
}

uchar4 __attribute__((kernel)) copyImage(uchar4 in, uint32_t x, uint32_t y) {
 return in;
}


short __attribute__((kernel)) getRedLaserLine(short in, uint32_t x) {
 uint32_t probe[probeWidth];
 int maxOfs = screenWidth - probeWidth;
 int32_t sum = 0;

 // init ring biffer
 for(int i = 0; i < probeWidth; i++) {
   uchar4 rgb = rsGetElementAt_uchar4(gInputFrame, i, x);
   int v = rgb.r;
   v = v*v;
   probe[i] = v;
   sum += v;
 }
 int probeRingBufferPtr = 0;
 int32_t maxSum = sum;
 int maxPoint = 0;

 for(int ofs = 1; ofs < maxOfs; ofs++) {
  sum -= probe[probeRingBufferPtr];
  uchar4 rgb = rsGetElementAt_uchar4(gInputFrame, ofs, x);
  int v = rgb.r;
  v = v*v;
  probe[probeRingBufferPtr] = v;
  sum += v;
  probeRingBufferPtr++;

  if(probeRingBufferPtr >= probeWidth) probeRingBufferPtr = 0;
  if(sum > maxSum) {
    maxSum = sum;
    maxPoint = ofs;
  }
 }
 if(maxSum < thresholdLevel) return -1;
 return maxPoint-probeWidth/2;
}

short __attribute__((kernel)) getGreenLaserLine(short in, uint32_t x) {
 short out;
 uint32_t probe[probeWidth];
 int maxOfs = screenWidth - probeWidth;
 int32_t sum = 0;

 // init ring biffer
 for(int i = 0; i < probeWidth; i++) {
   uchar4 rgb = rsGetElementAt_uchar4(gInputFrame, i, x);
   int v = rgb.g;
   v = v*v;
   sum += (probe[i] = v);// + (R-B)/2 - (R-G)/2);
 }
 int probeRingBufferPtr = 0;
 int32_t maxSum = sum;
 int maxPoint = 0;

 for(int ofs = 1; ofs < maxOfs; ofs++) {
  sum -= probe[probeRingBufferPtr];
  uchar4 rgb = rsGetElementAt_uchar4(gInputFrame, ofs, x);
  int v = rgb.g;
  v = v*v;
  sum += (probe[probeRingBufferPtr] = v); // + (R-B)/2 - (R-G)/2);
  probeRingBufferPtr++;

  if(probeRingBufferPtr >= probeWidth) probeRingBufferPtr = 0;
  if(sum > maxSum) {
    maxSum = sum;
    maxPoint = ofs;
  }
 }
 if(maxSum < thresholdLevel) return -1;
 return maxPoint-probeWidth/2;
}

//-------------------------------------------------------------------------------------------------
double tangentRedLaserValue, tangentGreenLaserValue;
double laserGreenL, laserRedL;
float thresholdMinZ, thresholdMaxZ;
float thresholdMinX, thresholdMaxX;
float thresholdMinY, thresholdMaxY;

float3 __attribute__((kernel)) getGreenLaserPointsCloud(float3 in, uint32_t x) {
  float3 out;
  short *p = (short*)rsGetElementAt(gGreenLaserPoints, x);
  short sreenPosW = p[0];

  if(sreenPosW < 0) {
   out.z = -2; out.x = 0;  out.y = 0;
   return out;
  }
  short sreenPosH = x;
  double tangentScreenW = tangentScreenCoefficientPerPixel * (sreenPosW - halfScreenWidth);
  out.z = laserGreenL / (tangentGreenLaserValue + tangentScreenW);
  out.x = out.z * tangentScreenW;
  double tangentScreenH = tangentScreenCoefficientPerPixel * (sreenPosH - halfScreenHeight);
  out.y = out.z * tangentScreenH;

  if(out.z > thresholdMaxZ || out.z < thresholdMinZ ||
     out.x > thresholdMaxX || out.x < thresholdMinX ||
     out.y > thresholdMaxY || out.y < thresholdMinY) {
   out.z = -1; out.x = 0; out.y = 0;
  }
  return out;
}

float3 __attribute__((kernel)) getRedLaserPointsCloud(float3 in, uint32_t x) {
  float3 out;
  short *p = (short*)rsGetElementAt(gRedLaserPoints, x);
  short sreenPosW = p[0];
  if(sreenPosW < 0) {
   out.z = -3; out.x = 0;  out.y = 0;
   return out;
  }
  short sreenPosH = x;
  double tangentScreenW = tangentScreenCoefficientPerPixel * (sreenPosW - halfScreenWidth);
  out.z = laserRedL / (tangentRedLaserValue - tangentScreenW);
  out.x = out.z * tangentScreenW;
  double tangentScreenH = tangentScreenCoefficientPerPixel * (sreenPosH - halfScreenHeight);
  out.y = out.z * tangentScreenH;

  if(out.z > thresholdMaxZ || out.z < thresholdMinZ ||
     out.x > thresholdMaxX || out.x < thresholdMinX ||
     out.y > thresholdMaxY || out.y < thresholdMinY) {
   out.z = -1; out.x = 0; out.y = 0;
  }
  return out;
}

//==================================================================================================
const static uchar4 histogramProbeLineR = {64, 0, 8, 255};
const static uchar4 histogramProbeLineG = {0, 64, 8, 255};
const static uchar4 histogramProbeLineWhite = {255, 255, 255, 255};
const static uchar4 backRgb = {0, 0, 0, 0};

static uchar4 setHistogram(uchar4 in, uint32_t x, uint32_t y) {
 if(y == histogramProbeLine) return histogramProbeLineWhite;
 if(y < (histogramStart-1) || y > (histogramStart+257)) return in;

 short *p = (short*)rsGetElementAt(gRedLaserPoints, histogramProbeLine);
 if(p[0] == x)  return histogramProbeLineR;
 p = (short*)rsGetElementAt(gGreenLaserPoints, histogramProbeLine);
 if(p[0] == x)  return histogramProbeLineG;

 if(y == histogramStart || y == (histogramStart+256)) return histogramProbeLineWhite;
 uchar4 out = backRgb;
 int yy = 255-(y-histogramStart);
 uchar4 probeRGB = rsGetElementAt_uchar4(gInputFrame, x, histogramProbeLine);
 if(probeRGB.r == yy) out.r = 255;
 if(probeRGB.g == yy) out.g = 255;
 if(probeRGB.b == yy) out.b = 255;
 if(probeRGB.a == yy) { out.b = 255; out.g = 255; }

 return out;
}
// */---------------------------------------------------------------------------

const static float3 gMonoMult = {0.299f/2, 0.587f/2, 0.114f/2};
uchar4 __attribute__((kernel)) setOutputImage(uchar4 in, uint32_t x, uint32_t y) {
  float4 rgb;
  float4 f4 = rsUnpackColor8888(in);
  float3 mono = dot(f4.rgb, gMonoMult);
  uchar4 out = rsPackColorTo8888(mono);
  short R = *((short*)rsGetElementAt(gRedLaserPoints, y));
  short G = *((short*)rsGetElementAt(gGreenLaserPoints, y));

  if(R == x) {
    out.a = out.r = 255; out.g = out.b = 0;
  }
  if(G == x) {
    out.a = out.g = 255; out.r = out.b = 0;
  }

  return setHistogram(out, x, y);
//  return out;
}

//================================================================================================
rs_allocation gColorMapImage;
float kRatioXY, kRatioZ8, kRatioZ16;
float minX, minY, maxZ;

uchar4 __attribute__((kernel)) clearImage(uchar4 in, uint32_t x, uint32_t y) {
 uchar4 out;
 out.r = out.g = out.b = 0;
 out.a = 0;
 return out;
}

void __attribute__((kernel)) setColorMapImage8(float3 in, uint32_t x) {
  if(in.z <= 0) return;
  uchar4 rgb;
  int imgX = (in.x - minX) * kRatioXY;
  int imgY = (in.y - minY) * kRatioXY;
  uint32_t imgZ = (maxZ - in.z) * kRatioZ8;

  uchar4 rgb_old = rsGetElementAt_uchar4(gColorMapImage, imgX, imgY);
  if(rgb_old.a != 0) {
   uint32_t imgZ_old = (uint32_t)rgb_old.b;
   imgZ = (imgZ_old + imgZ)/2;
  }

  rgb.b = (uchar)imgZ;
  rgb.g = (uchar)imgZ;
  rgb.r = (uchar)imgZ;
  rgb.a = 255;
  rsSetElementAt_uchar4(gColorMapImage, rgb, imgX, imgY);
}

void __attribute__((kernel)) setColorMapImage16(float3 in, uint32_t x) {
  if(in.z <= 0) return;
  uchar4 rgb;
  int imgX = (in.x - minX) * kRatioXY;
  int imgY = (in.y - minY) * kRatioXY;
  int imgZ = (maxZ - in.z) * kRatioZ16;

  uchar4 rgb_old = rsGetElementAt_uchar4(gColorMapImage, imgX, imgY);
  if(rgb_old.a != 0) {
   int imgZ_old = ((int)rgb_old.r << 8) + (int)rgb_old.g;
   imgZ = (imgZ_old + imgZ)/2;
  }

  rgb.a = 255;
  rgb.r = 0;
  rgb.g = (uchar)(imgZ >> 8);
  rgb.b = (uchar)imgZ;
  rsSetElementAt_uchar4(gColorMapImage, rgb, imgX, imgY);
}


int maxGapSize;

uchar4 __attribute__((kernel)) fillGap8(uchar4 px, uint32_t x, uint32_t y) {
  if(px.a != 0) return px;
  float imgZ1;
  if(x == 0) imgZ1 = 0;
  else {
   uchar4 px1 = rsGetElementAt_uchar4(gColorMapImage, x-1, y);
   imgZ1 = px1.b;
  }
  float imgZ2 = 0;
  int i = x+1;
  while(i < screenWidth && (i-x) < maxGapSize) {
   uchar4 px2 = rsGetElementAt_uchar4(gColorMapImage, i++, y);
   if(px2.a != 0) {
    imgZ2 = px2.b;
    px.b = px.g = px.r = imgZ1 + (imgZ2-imgZ1)/(i-x) + 0.5;
    px.a = 255;
    return px;
   }
  }
  return px;
}


uchar4 __attribute__((kernel)) fillGap16(uchar4 px, uint32_t x, uint32_t y) {
  if(px.a != 0) return px;
  float imgZ1;
  if(x == 0) imgZ1 = 0;
  else {
   uchar4 px1 = rsGetElementAt_uchar4(gColorMapImage, x-1, y);
   imgZ1 = ((uint32_t)px1.g << 8) + (uint32_t)px1.b;
  }
  float imgZ2 = 0;
  int i = x+1;
  while(i < screenWidth && (i-x) < maxGapSize) {
   uchar4 px2 = rsGetElementAt_uchar4(gColorMapImage, i++, y);
   if(px2.a != 0) {
    imgZ2 = ((uint32_t)px2.g << 8) + (uint32_t)px2.b;
    uint32_t imgZ = imgZ1 + (imgZ2-imgZ1)/(i-x) + 0.5;
    px.r = 0;
    px.g = (uchar)(imgZ >> 8);
    px.b = (uchar)imgZ;
    px.a = 255;
    return px;
   }
  }
  return px;
}
