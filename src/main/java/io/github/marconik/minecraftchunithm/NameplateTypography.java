package io.github.marconik.minecraftchunithm;

/** Advances of Minecraft 26.3's bundled uniform font, in logical font pixels. */
final class NameplateTypography {
    private NameplateTypography(){}
    // Printable ASCII 33..126, measured from the bundled Unifont glyph ink bounds.
    // UnihexProvider uses integer width / 2 + 1 and a half-pixel bold offset.
    private static final int[] ASCII={
        1,3,4,4,4,4,1,2,2,4,4,2,3,2,4,4,3,4,4,4,4,4,4,4,4,2,2,3,4,3,4,4,
        4,4,4,4,4,4,4,4,3,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,2,4,2,4,4,
        2,4,4,4,4,4,3,4,4,3,3,4,3,4,4,4,4,4,4,4,3,4,4,4,4,4,4,3,1,3,4};
    static double width(String text,boolean bold){
        double total=text.codePoints().mapToDouble(c->{
            double advance=c==32?4:c>=33&&c<=126?ASCII[c-33]:9;
            return advance+(bold?.5:0);
        }).sum();
        // TextDisplayRenderer centres on Font.width(), which rounds upward.
        return Math.ceil(total);
    }
}
