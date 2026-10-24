package io.github.marconik.minecraftchunithm;

/** Keep existing layer order but compress the entire facade to a few millimetres. */
final class FacadeSurface {
    static final float DEPTH_SCALE=.06f;
    static double depth(double original){return 1+(original-1)*DEPTH_SCALE;}
    private FacadeSurface(){}
}
