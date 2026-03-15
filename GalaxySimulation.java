import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;

/**
 * ══════════════════════════════════════════════════════════════════
 *  GALAXY SIMULATION v8  — ALL FEATURES
 *
 *  ✦ 3D rotation         right-click drag
 *  ✦ Warp flythrough     F key
 *  ✦ Gravitational lens  near black hole
 *  ✦ Supernovae          random explosions
 *  ✦ Dark matter halo    D key toggle
 *  ✦ Star lifecycle      stars age & colour-shift
 *  ✦ Gravity well grid   G key toggle
 *  ✦ Wavelength modes    V/I/X/R keys
 *  ✦ Place your own stars P key + click
 *  ✦ Keyboard shortcuts  H key help overlay
 *  ✦ Live stats panel    always visible
 *  ✦ GIF recording       R key (3 sec, saves to Desktop)
 *
 *  javac GalaxySimulation.java
 *  java  GalaxySimulation
 * ══════════════════════════════════════════════════════════════════
 */
public class GalaxySimulation extends JFrame {
    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "true");
        SwingUtilities.invokeLater(() -> new GalaxySimulation().setVisible(true));
    }
    public GalaxySimulation() {
        setTitle("Galaxy Simulation v8");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        SimPanel p = new SimPanel();
        add(p); pack(); setLocationRelativeTo(null);
    }
}

// ══ DATA CLASSES ═══════════════════════════════════════════════════

class Star {
    double r, angle, omega;
    float  diskZ, cr, cg, cb, baseCr, baseCg, baseCb;
    int    glow, gid;
    String tip;
    float  twPhase, twSpeed;
    // lifecycle
    double age = 0, maxAge;
    boolean isSupergiant = false;

    void tick(double dt) {
        angle += omega * dt;
        twPhase += (float)(twSpeed * dt);
        age += dt;
    }
    float alpha() { return 0.62f + 0.38f * (float)Math.sin(twPhase); }

    /** Shift colour based on age fraction */
    void updateLifecycle() {
        if (maxAge <= 0) return;
        double f = Math.min(1.0, age / maxAge);
        if (isSupergiant) {
            // Blue → white → yellow → red giant
            if (f < 0.5) {
                double t = f / 0.5;
                cr = (float)(baseCr + (255 - baseCr) * t);
                cg = (float)(baseCg + (255 - baseCg) * t);
                cb = (float)(baseCb * (1 - t) + 200 * t);
            } else {
                double t = (f - 0.5) / 0.5;
                cr = 255;
                cg = (float)(255 * (1 - t * 0.6));
                cb = (float)(200 * (1 - t));
            }
        }
    }
}

class Supernova {
    double x, y;        // world coords
    float  radius, maxR, alpha;
    float  cr, cg, cb;
    boolean dead = false;

    Supernova(double x, double y) {
        this.x = x; this.y = y;
        maxR = 2.2f + (float)(Math.random() * 1.2);
        alpha = 1.0f;
        // Warm white-blue flash
        cr = 200 + (float)(Math.random() * 55);
        cg = 200 + (float)(Math.random() * 55);
        cb = 255;
    }
    void tick(double dt) {
        radius += (float)(dt * 1.4);
        alpha   = Math.max(0, 1.0f - radius / maxR);
        if (radius > maxR) dead = true;
    }
}

class NebulaCloud {
    double angle, r;
    float  size, cr, cg, cb, alpha, driftPhase, driftSpeed;
    boolean dark;
    void tick(float dt) { driftPhase += driftSpeed * dt; }
}

class BGStar {
    float x, y, vx, vy, br, twPhase, twSpeed;
    int   rc, gc, bc, size;
    void tick(float dt) {
        x += vx * dt; y += vy * dt; twPhase += twSpeed * dt;
        if (x < 0) x += 1280; if (x >= 1280) x -= 1280;
        if (y < 0) y += 580;  if (y >= 580)  y -= 580;
    }
    float alpha() { return Math.min(1f, br * (0.65f + 0.35f * (float)Math.sin(twPhase))); }
}

// ══ GALAXY ══════════════════════════════════════════════════════════

class Galaxy {
    final List<Star>        stars   = new ArrayList<>();
    final List<NebulaCloud> nebulae = new ArrayList<>();
    String name; Color labelCol;

    void build(int N, int arms, boolean bulge, int gid, Random rng) {
        stars.clear(); nebulae.clear();

        // ── Disk stars ──
        for (int i = 0; i < N; i++) {
            Star s = new Star();
            double scR = gid==1 ? 0.75 : 1.4;
            s.r = expR(rng, scR);
            s.omega = 1.0 / Math.max(s.r, 0.08);
            if (arms > 0) {
                int arm = rng.nextInt(arms);
                // Andromeda (gid=0): tighter arms, less scatter = more defined spirals
                double tight  = (gid==3) ? 3.0 : (gid==0) ? 2.8 : 2.5;
                double scatter= (gid==3) ? 0.05+s.r*0.025 : (gid==0) ? 0.07+s.r*0.04 : 0.10+s.r*0.06;
                double ls = Math.log(s.r/0.05+1.0)*tight;
                s.angle = arm*(2*Math.PI/arms)+ls+rng.nextGaussian()*scatter;
            } else {
                s.angle = rng.nextDouble()*2*Math.PI;
            }
            s.r = Math.max(0.04, s.r*(1+rng.nextGaussian()*0.012));
            // Sombrero 84° edge-on — extremely flat disc
            s.diskZ = gid==1 ? (float)(rng.nextGaussian()*0.012) : 0f;
            s.gid = gid;
            colorDisk(s, gid, arms, rng);
            s.baseCr=s.cr; s.baseCg=s.cg; s.baseCb=s.cb;
            s.twPhase=rng.nextFloat()*6.28f; s.twSpeed=0.3f+rng.nextFloat()*1.2f;
            s.maxAge = s.isSupergiant ? 60+rng.nextDouble()*120 : 0;
            stars.add(s);
        }

        // ── Dense core stars — packed inner region ──
        // Sombrero: huge FLAT spheroid — seen edge-on it looks like a bright lens
        // Key: diskZ must be small or the edge-on projection makes it look round
        int coreN = gid==1 ? N*2 : N*3/4;
        for (int i = 0; i < coreN; i++) {
            Star s = new Star();
            double rb, phi;
            if(gid==1){
                // Sombrero: oblate spheroid — large r, very small Z (flat lens shape)
                rb  = Math.pow(rng.nextDouble(), 0.38) * 0.75;
                phi = rng.nextGaussian() * 0.08; // very thin in Z — looks like bright oval edge-on
            } else if(gid==0){
                rb  = Math.pow(rng.nextDouble(), 0.50) * 0.5;
                phi = (rng.nextDouble()-0.5)*Math.PI*0.25;
            } else {
                rb  = Math.pow(rng.nextDouble(), 0.60) * 0.40;
                phi = (rng.nextDouble()-0.5)*Math.PI*0.15;
            }
            s.r = rb*Math.cos(phi); s.diskZ=(float)(rb*Math.sin(phi));
            if(s.r<0.01) s.r=0.01;
            s.angle=rng.nextDouble()*2*Math.PI; s.omega=1.0/Math.max(s.r,0.05);
            if(gid==0){
                s.cr=255; s.cg=clamp(190+rng.nextInt(45)); s.cb=clamp(80+rng.nextInt(60));
            } else if(gid==1){
                s.cr=255; s.cg=clamp(225+rng.nextInt(25)); s.cb=clamp(170+rng.nextInt(55));
            } else if(gid==3){
                s.cr=255; s.cg=clamp(215+rng.nextInt(35)); s.cb=clamp(140+rng.nextInt(60));
            } else {
                s.cr=255; s.cg=clamp(218+rng.nextInt(30)); s.cb=clamp(145+rng.nextInt(75));
            }
            s.baseCr=s.cr; s.baseCg=s.cg; s.baseCb=s.cb;
            s.glow=1; s.gid=gid; s.tip="Bulge star";
            s.twPhase=rng.nextFloat()*6.28f; s.twSpeed=0.4f+rng.nextFloat();
            stars.add(s);
        }

        // ── Bright core giant stars ──
        for (int i = 0; i < 120; i++) {
            Star s = new Star();
            s.r = Math.pow(rng.nextDouble(), 2.5)*0.12;
            s.angle=rng.nextDouble()*2*Math.PI; s.omega=1.0/Math.max(s.r,0.05);
            s.cr=255; s.cg=clamp(245+rng.nextInt(10)); s.cb=clamp(200+rng.nextInt(55));
            s.baseCr=s.cr; s.baseCg=s.cg; s.baseCb=s.cb;
            s.glow=2; s.gid=gid; s.diskZ=0f; s.tip="Core giant";
            s.twPhase=rng.nextFloat()*6.28f; s.twSpeed=1.8f; stars.add(s);
        }

        // ── HII regions — bright pink emission zones along arms ──
        if(arms>0){
            for(int i=0;i<40;i++){
                Star s=new Star();
                int arm=rng.nextInt(arms);
                s.r=0.3+rng.nextDouble()*1.0;
                double ls=Math.log(s.r/0.05+1.0)*(gid==3?3.0:2.5);
                s.angle=arm*(2*Math.PI/arms)+ls+rng.nextGaussian()*0.08;
                s.omega=1.0/Math.max(s.r,0.08); s.diskZ=0f; s.gid=gid;
                s.cr=255; s.cg=clamp(80+rng.nextInt(60)); s.cb=clamp(120+rng.nextInt(80));
                s.baseCr=s.cr; s.baseCg=s.cg; s.baseCb=s.cb;
                s.glow=2; s.tip="HII Region (Star forming)";
                s.twPhase=rng.nextFloat()*6.28f; s.twSpeed=0.5f+rng.nextFloat()*0.5f;
                stars.add(s);
            }
        }

        // ── Black hole ──
        Star bh=new Star(); bh.r=0; bh.angle=0; bh.omega=0; bh.diskZ=0;
        bh.cr=255; bh.cg=255; bh.cb=255; bh.glow=4; bh.gid=gid;
        bh.baseCr=255; bh.baseCg=255; bh.baseCb=255;
        String bhMass = gid==1?"~1 Billion M\u2609":gid==3?"Sgr A*: 4 Million M\u2609":"~140 Million M\u2609";
        bh.tip="Supermassive Black Hole\n"+(name!=null?name:"")+"\nMass: "+bhMass;
        bh.twPhase=0; bh.twSpeed=0.5f; stars.add(0,bh);

        buildNebulae(arms, gid, rng);
    }

    void buildNebulae(int arms, int gid, Random rng) {
        // Emission nebulae — smaller, more subtle
        int emCount = arms>0 ? 35 : 12;
        for (int i=0; i<emCount; i++) {
            NebulaCloud n=new NebulaCloud();
            n.r=0.20+rng.nextDouble()*1.1;
            if(arms>0){
                int a=rng.nextInt(arms);
                double tight=gid==3?3.0:2.5;
                n.angle=a*(2*Math.PI/arms)+Math.log(n.r/0.05+1)*tight+(rng.nextDouble()-.5)*.5;
            } else n.angle=rng.nextDouble()*2*Math.PI;
            n.size=0.03f+rng.nextFloat()*0.08f; // much smaller
            n.dark=false;
            if(gid==0){
                double d=rng.nextDouble();
                if(d<0.35){n.cr=60; n.cg=100; n.cb=255; n.alpha=0.12f;}
                else if(d<0.65){n.cr=255; n.cg=60; n.cb=100; n.alpha=0.10f;}
                else{n.cr=180; n.cg=60; n.cb=255; n.alpha=0.09f;}
            } else if(gid==1){
                n.cr=255; n.cg=clamp(140+rng.nextInt(60)); n.cb=40; n.alpha=0.09f;
            } else if(gid==3){
                double d=rng.nextDouble();
                if(d<0.3){n.cr=255; n.cg=60; n.cb=80; n.alpha=0.13f;}
                else if(d<0.6){n.cr=60; n.cg=120; n.cb=255; n.alpha=0.11f;}
                else{n.cr=180; n.cg=255; n.cb=80; n.alpha=0.08f;}
            } else {
                n.cr=clamp(100+rng.nextInt(155)); n.cg=clamp(80+rng.nextInt(100)); n.cb=255; n.alpha=0.10f;
            }
            n.driftPhase=rng.nextFloat()*6.28f; n.driftSpeed=0.06f+rng.nextFloat()*0.12f;
            nebulae.add(n);
        }
        // Dark dust lanes — fewer for Andromeda, more subtle alpha
        int dkCount = gid==1 ? 28 : gid==0 ? 10 : arms>0 ? 14 : 8;
        for (int i=0; i<dkCount; i++) {
            NebulaCloud n=new NebulaCloud();
            n.r=gid==1?0.05+rng.nextDouble()*0.9:0.20+rng.nextDouble()*1.0;
            if(arms>0){
                int a=rng.nextInt(arms);
                double tight=gid==3?3.0:2.5;
                n.angle=a*(2*Math.PI/arms)+Math.log(n.r/0.05+1)*tight+0.2+(rng.nextDouble()-.5)*.5;
            } else n.angle=rng.nextDouble()*2*Math.PI;
            n.size=gid==1?0.12f+rng.nextFloat()*0.20f:0.05f+rng.nextFloat()*0.10f;
            n.dark=true; n.cr=0; n.cg=0; n.cb=0;
            // Andromeda dark lanes much more subtle — real ones are thin filaments
            n.alpha=gid==1?0.70f+rng.nextFloat()*0.20f:gid==0?0.28f+rng.nextFloat()*0.18f:0.45f+rng.nextFloat()*0.25f;
            n.driftPhase=rng.nextFloat()*6.28f; n.driftSpeed=0.02f+rng.nextFloat()*0.06f;
            nebulae.add(n);
        }
    }

    private double expR(Random rng, double sc){return Math.min(-sc*Math.log(1-rng.nextDouble()*0.98),sc*3.8);}

    private void colorDisk(Star s, int gid, int arms, Random rng) {
        if(s.r<0.18){
            // Inner region — warm old stars for all galaxies
            s.cr=255; s.cg=clamp(230+rng.nextInt(20)); s.cb=clamp(130+rng.nextInt(60)); s.glow=1; s.tip="Core K/G Giant";
            return;
        }
        if(gid==1){ // SOMBRERO — old red/orange stellar population, no young blues
            double d=rng.nextDouble();
            if(d<0.6){ s.cr=255; s.cg=clamp(165+rng.nextInt(50)); s.cb=clamp(60+rng.nextInt(55)); s.glow=0; s.tip="K-type (old)"; }
            else if(d<0.85){ s.cr=255; s.cg=clamp(200+rng.nextInt(40)); s.cb=clamp(120+rng.nextInt(50)); s.glow=0; s.tip="G-type"; }
            else { s.cr=255; s.cg=clamp(220+rng.nextInt(25)); s.cb=clamp(160+rng.nextInt(50)); s.glow=0; s.tip="F-type"; }
        } else if(gid==0){ // ANDROMEDA — mix: warm core, blue-white arms
            double d=rng.nextDouble();
            if(arms>0&&d<0.04){ s.cr=60; s.cg=120; s.cb=255; s.glow=3; s.tip="O-type Supergiant"; s.isSupergiant=true; }
            else if(arms>0&&d<0.22){ s.cr=clamp(120+rng.nextInt(60)); s.cg=clamp(165+rng.nextInt(50)); s.cb=255; s.glow=1; s.tip="B-type"; }
            else if(d<0.42){ s.cr=clamp(200+rng.nextInt(40)); s.cg=clamp(215+rng.nextInt(30)); s.cb=255; s.glow=0; s.tip="A-type"; }
            else if(d<0.65){ s.cr=255; s.cg=clamp(225+rng.nextInt(25)); s.cb=clamp(160+rng.nextInt(50)); s.glow=0; s.tip="F/G-type"; }
            else if(d<0.82){ s.cr=255; s.cg=clamp(185+rng.nextInt(40)); s.cb=clamp(80+rng.nextInt(50)); s.glow=0; s.tip="K-type"; }
            else { s.cr=255; s.cg=clamp(120+rng.nextInt(50)); s.cb=clamp(40+rng.nextInt(40)); s.glow=0; s.tip="M-type"; }
        } else if(gid==3){ // MILKY WAY — rich mix, visible bar, blue arm stars
            double d=rng.nextDouble();
            if(arms>0&&d<0.05){ s.cr=70; s.cg=140; s.cb=255; s.glow=3; s.tip="O-type Supergiant"; s.isSupergiant=true; }
            else if(arms>0&&d<0.20){ s.cr=clamp(130+rng.nextInt(60)); s.cg=clamp(175+rng.nextInt(45)); s.cb=255; s.glow=1; s.tip="B-type"; }
            else if(d<0.38){ s.cr=clamp(210+rng.nextInt(35)); s.cg=clamp(222+rng.nextInt(28)); s.cb=255; s.glow=0; s.tip="A-type"; }
            else if(d<0.60){ s.cr=255; s.cg=clamp(228+rng.nextInt(22)); s.cb=clamp(170+rng.nextInt(45)); s.glow=0; s.tip="G-type (Sun-like)"; }
            else if(d<0.78){ s.cr=255; s.cg=clamp(175+rng.nextInt(45)); s.cb=clamp(75+rng.nextInt(55)); s.glow=0; s.tip="K-type"; }
            else { s.cr=255; s.cg=clamp(100+rng.nextInt(55)); s.cb=clamp(40+rng.nextInt(35)); s.glow=0; s.tip="M-type (Red dwarf)"; }
        } else { // generic
            double d=rng.nextDouble();
            if(arms>0&&d<0.05){ s.cr=70; s.cg=130; s.cb=255; s.glow=3; s.tip="O-type"; s.isSupergiant=true; }
            else if(arms>0&&d<0.28){ s.cr=clamp(135+rng.nextInt(55)); s.cg=clamp(175+rng.nextInt(45)); s.cb=255; s.glow=1; s.tip="B-type"; }
            else if(d<0.56){ s.cr=clamp(210+rng.nextInt(35)); s.cg=clamp(220+rng.nextInt(28)); s.cb=255; s.glow=0; s.tip="A-type"; }
            else { s.cr=clamp(215+rng.nextInt(30)); s.cg=clamp(195+rng.nextInt(35)); s.cb=clamp(148+rng.nextInt(65)); s.glow=0; s.tip="K/M-type"; }
        }
    }

    static int clamp(int v){return Math.max(0,Math.min(255,v));}

    void tick(double dt){
        for(Star s:stars){s.tick(dt);s.updateLifecycle();}
        for(NebulaCloud n:nebulae) n.tick((float)dt);
    }
}

// ══ RENDERER ════════════════════════════════════════════════════════

class Renderer {
    final int W, H;
    final int[] px;
    final BufferedImage img;

    static final int[]     GR = {1,1,2,4,7};
    static final float[][] GK;
    static {
        GK = new float[GR.length][];
        for (int k = 0; k < GR.length; k++) {
            int r = GR[k], d = 2*r+1; GK[k] = new float[d*d];
            float inv = 1f / (r*r + 0.5f);
            for (int dy=-r; dy<=r; dy++) for (int dx=-r; dx<=r; dx++) {
                float f = Math.max(0, 1f - (dx*dx+dy*dy)*inv);
                GK[k][(dy+r)*d+(dx+r)] = f*f;
            }
        }
    }

    Renderer(int W, int H) {
        this.W=W; this.H=H;
        img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        px  = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
    }

    void clear() { Arrays.fill(px, 0); }

    void dot(int sx, int sy, float cr, float cg, float cb, int ki) {
        ki = Math.min(GK.length-1, Math.max(0, ki));
        int r = GR[ki]; if (sx+r<0||sx-r>=W||sy+r<0||sy-r>=H) return;
        // ki=0: single pixel, ki=1: also single pixel, ki=2+: kernel glow
        if (ki <= 1) {
            if (sx<0||sx>=W||sy<0||sy>=H) return;
            int idx=sy*W+sx, c=px[idx];
            px[idx]=(Math.min(255,((c>>16)&0xFF)+(int)cr)<<16)|(Math.min(255,((c>>8)&0xFF)+(int)cg)<<8)|Math.min(255,((c)&0xFF)+(int)cb);
            return;
        }
        float[] k = GK[ki]; int d = 2*r+1;
        for (int y=Math.max(0,sy-r); y<=Math.min(H-1,sy+r); y++) {
            int base=y*W, kb=(y-sy+r)*d;
            for (int x=Math.max(0,sx-r); x<=Math.min(W-1,sx+r); x++) {
                float kv=k[kb+(x-sx+r)]; if(kv<=0) continue;
                int idx=base+x, c=px[idx];
                px[idx]=(Math.min(255,((c>>16)&0xFF)+(int)(cr*kv))<<16)
                       |(Math.min(255,((c>> 8)&0xFF)+(int)(cg*kv))<<8)
                       | Math.min(255,((c    )&0xFF)+(int)(cb*kv));
            }
        }
    }

    void nebula(int cx,int cy,int radius,float cr,float cg,float cb,float alpha,boolean dark){
        if(radius<1||radius>Math.max(W,H)) return; // safety guard
        int x0=Math.max(0,cx-radius),x1=Math.min(W-1,cx+radius);
        int y0=Math.max(0,cy-radius),y1=Math.min(H-1,cy+radius);
        float r2i=1f/(radius*radius+1);
        for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++){
            float dx=x-cx,dy=y-cy,d2=dx*dx+dy*dy; if(d2>radius*radius) continue;
            float f=(1f-d2*r2i); f=f*f*f; float a=alpha*f;
            int idx=y*W+x,c=px[idx],nr=(c>>16)&0xFF,ng=(c>>8)&0xFF,nb=c&0xFF;
            if(dark){ px[idx]=(Math.max(0,(int)(nr*(1-a*.85f)))<<16)|(Math.max(0,(int)(ng*(1-a*.85f)))<<8)|Math.max(0,(int)(nb*(1-a*.85f))); }
            else { px[idx]=(Math.min(255,nr+(int)(cr*a*180))<<16)|(Math.min(255,ng+(int)(cg*a*180))<<8)|Math.min(255,nb+(int)(cb*a*180)); }
        }
    }

    /** Draw dark matter halo — faint purple radial glow */
    void darkMatterHalo(int cx, int cy, int radius) {
        if(radius<1) return;
        radius=Math.min(radius,Math.max(W,H)); // cap radius
        int x0=Math.max(0,cx-radius),x1=Math.min(W-1,cx+radius);
        int y0=Math.max(0,cy-radius),y1=Math.min(H-1,cy+radius);
        float r2i=1f/(radius*radius+1f);
        for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++){
            float dx=x-cx,dy=y-cy,d2=dx*dx+dy*dy; if(d2>radius*radius) continue;
            float f=(1f-d2*r2i); f=(float)Math.sqrt(f)*0.18f;
            int idx=y*W+x,c=px[idx];
            int nr=Math.min(255,((c>>16)&0xFF)+(int)(80*f));
            int ng=Math.min(255,((c>> 8)&0xFF)+(int)(20*f));
            int nb=Math.min(255,((c    )&0xFF)+(int)(140*f));
            px[idx]=(nr<<16)|(ng<<8)|nb;
        }
    }

    /** Gravity well grid warped by gravitational potential */
    void gravityWellGrid(int cx, int cy, double sc, double cosI) {
        int gridSpacing = 30, cols = W/gridSpacing+2, rows = H/gridSpacing+2;
        // For each grid intersection, compute displacement toward galaxy center
        for (int gx = -1; gx < cols; gx++) {
            for (int gy = -1; gy < rows; gy++) {
                int x1s = gx*gridSpacing, y1s = gy*gridSpacing;
                int x2s = (gx+1)*gridSpacing, y2s = gy*gridSpacing;
                int x3s = gx*gridSpacing, y3s = (gy+1)*gridSpacing;
                // Warp horizontal line
                warpLine(cx,cy,x1s,y1s,x2s,y2s,sc*cosI);
                // Warp vertical line
                warpLine(cx,cy,x3s,y3s,x3s,y3s+gridSpacing,sc*cosI);
            }
        }
    }

    private void warpLine(int cx,int cy,int x1,int y1,int x2,int y2,double strength){
        int steps=Math.max(2,(int)(Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1))/4));
        int px2=-1,py2=-1;
        for(int s=0;s<=steps;s++){
            float t=(float)s/steps;
            int rx=(int)(x1+t*(x2-x1)), ry=(int)(y1+t*(y2-y1));
            double dx=rx-cx,dy=ry-cy,r2=dx*dx+dy*dy+200;
            double warp=strength*12000/(r2);
            int wx=(int)(rx-dx*warp), wy=(int)(ry-dy*warp);
            if(px2>=0&&wx>=0&&wx<W&&wy>=0&&wy<H&&px2>=0&&px2<W&&py2>=0&&py2<H)
                thinLine(px2,py2,wx,wy,new Color(25,50,80,60));
            px2=wx; py2=wy;
        }
    }

    private void thinLine(int x1,int y1,int x2,int y2,Color col){
        int dx=Math.abs(x2-x1),dy=Math.abs(y2-y1);
        int sx=x1<x2?1:-1,sy=y1<y2?1:-1,err=dx-dy;
        int cr2=col.getRed(),cg2=col.getGreen(),cb2=col.getBlue(),a=col.getAlpha();
        while(true){
            if(x1>=0&&x1<W&&y1>=0&&y1<H){
                int idx=y1*W+x1,c=px[idx];
                px[idx]=(Math.min(255,((c>>16)&0xFF)+cr2*a/255)<<16)
                       |(Math.min(255,((c>> 8)&0xFF)+cg2*a/255)<<8)
                       | Math.min(255,((c    )&0xFF)+cb2*a/255);
            }
            if(x1==x2&&y1==y2) break;
            int e2=2*err;
            if(e2>-dy){err-=dy;x1+=sx;}
            if(e2< dx){err+=dx;y1+=sy;}
        }
    }

    /** Gravitational lensing: bend pixels near a massive object */
    void gravLens(int cx, int cy, int radius) {
        int[] buf = Arrays.copyOf(px, px.length);
        int x0=Math.max(0,cx-radius),x1=Math.min(W-1,cx+radius);
        int y0=Math.max(0,cy-radius),y1=Math.min(H-1,cy+radius);
        double r2max=radius*radius;
        for (int y=y0; y<=y1; y++) for (int x=x0; x<=x1; x++) {
            double dx=x-cx,dy=y-cy,r2=dx*dx+dy*dy;
            if(r2>r2max||r2<4) continue;
            double r=Math.sqrt(r2);
            // Einstein ring deflection: deflect pixels inward (map this pixel FROM a slightly displaced source)
            double deflect = radius*radius*0.25/r2;
            double sx2=cx+dx*(1+deflect), sy2=cy+dy*(1+deflect);
            int six=(int)Math.min(W-1,Math.max(0,sx2));
            int siy=(int)Math.min(H-1,Math.max(0,sy2));
            px[y*W+x] = buf[siy*W+six];
            // Brighten the Einstein ring band
            if(Math.abs(r-radius*0.45)<radius*0.08){
                int c=px[y*W+x];
                px[y*W+x]=(Math.min(255,((c>>16)&0xFF)+40)<<16)|(Math.min(255,((c>>8)&0xFF)+40)<<8)|Math.min(255,((c)&0xFF)+50);
            }
        }
    }

    /** Supernova expanding ring */
    void supernova(int sx, int sy, float radius, float alpha, float cr, float cg, float cb) {
        int r=(int)radius; if(r<1) return;
        // Core flash
        if(r<8) { dot(sx,sy,(int)(cr*alpha),(int)(cg*alpha),(int)(cb*alpha),4); return; }
        // Ring: draw circle perimeter with glow
        for(int thickness=0;thickness<3;thickness++){
            int rt=r-thickness;
            double step=1.0/rt;
            for(double a=0;a<2*Math.PI;a+=step){
                int px2=(int)(sx+rt*Math.cos(a)), py2=(int)(sy+rt*Math.sin(a));
                if(px2>=0&&px2<W&&py2>=0&&py2<H){
                    float k=alpha*(1-thickness*0.3f);
                    dot(px2,py2,cr*k,cg*k,cb*k,1);
                }
            }
        }
    }

    /** Apply wavelength colour transform to entire buffer */
    void applyWavelength(int mode) {
        // 0=visible (none), 1=infrared, 2=xray, 3=radio
        if (mode==0) return;
        for (int i=0; i<px.length; i++) {
            int c=px[i], r=(c>>16)&0xFF, g=(c>>8)&0xFF, b=c&0xFF;
            int lum=(r*77+g*150+b*29)>>8;
            if(mode==1){ // Infrared: warm orange-red glow, cool→dark
                px[i]=(Math.min(255,(int)(lum*1.6))<<16)|(Math.min(255,(int)(lum*0.7))<<8)|Math.min(255,(int)(lum*0.3));
            } else if(mode==2){ // X-Ray: intense=bright blue, rest=black
                int x2=Math.min(255,(int)(lum*2.2));
                px[i]=(Math.min(255,(int)(x2*0.2))<<16)|(Math.min(255,(int)(x2*0.5))<<8)|x2;
            } else if(mode==3){ // Radio: purple/magenta emission map
                px[i]=(Math.min(255,(int)(lum*1.4))<<16)|(Math.min(255,(int)(lum*0.3))<<8)|Math.min(255,(int)(lum*1.6));
            }
        }
    }

    void dustLane(int cx,int cy,int hw,int hh){
        for(int y=Math.max(0,cy-hh);y<=Math.min(H-1,cy+hh);y++)
            for(int x=Math.max(0,cx-hw);x<=Math.min(W-1,cx+hw);x++){
                float fx=(float)(x-cx)/hw,fy=(float)(y-cy)/hh,d2=fx*fx+fy*fy; if(d2>1) continue;
                float dk=0.91f*(1-d2); int idx=y*W+x,c=px[idx];
                px[idx]=((int)(((c>>16)&0xFF)*(1-dk))<<16)|((int)(((c>>8)&0xFF)*(1-dk))<<8)|(int)(((c)&0xFF)*(1-dk));
            }
    }

    void bloom(float strength,int passes,int blurR,float thr){
        int bW=W/4,bH=H/4; float[] sm=new float[bW*bH*3];
        for(int y=0;y<bH;y++) for(int x=0;x<bW;x++){
            float r=0,g=0,b=0;
            for(int dy=0;dy<4;dy++) for(int dx=0;dx<4;dx++){int s=px[(y*4+dy)*W+x*4+dx];r+=(s>>16)&0xFF;g+=(s>>8)&0xFF;b+=s&0xFF;}
            int i=(y*bW+x)*3;sm[i]=Math.max(0,r*.0625f-thr);sm[i+1]=Math.max(0,g*.0625f-thr);sm[i+2]=Math.max(0,b*.0625f-thr);
        }
        for(int p=0;p<passes;p++) sm=blur(sm,bW,bH,blurR);
        for(int y=0;y<H;y++) for(int x=0;x<W;x++){
            int bx=Math.min(bW-1,x/4),by=Math.min(bH-1,y/4),i=(by*bW+bx)*3,c=px[y*W+x];
            px[y*W+x]=(Math.min(255,((c>>16)&0xFF)+(int)(sm[i]*strength))<<16)|(Math.min(255,((c>>8)&0xFF)+(int)(sm[i+1]*strength))<<8)|Math.min(255,((c)&0xFF)+(int)(sm[i+2]*strength));
        }
    }
    private float[] blur(float[] src,int w,int h,int rad){
        float[] t=new float[src.length],d=new float[src.length]; float inv=1f/(2*rad+1);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++){float r=0,g=0,b=0;for(int dx=-rad;dx<=rad;dx++){int nx=Math.max(0,Math.min(w-1,x+dx)),i=(y*w+nx)*3;r+=src[i];g+=src[i+1];b+=src[i+2];}int i=(y*w+x)*3;t[i]=r*inv;t[i+1]=g*inv;t[i+2]=b*inv;}
        for(int y=0;y<h;y++) for(int x=0;x<w;x++){float r=0,g=0,b=0;for(int dy=-rad;dy<=rad;dy++){int ny=Math.max(0,Math.min(h-1,y+dy)),i=(ny*w+x)*3;r+=t[i];g+=t[i+1];b+=t[i+2];}int i=(y*w+x)*3;d[i]=r*inv;d[i+1]=g*inv;d[i+2]=b*inv;}
        return d;
    }
    /** Smooth radial core glow — simulates integrated light from millions of stars */
    void galaxyCore(int cx, int cy, int radius, float cr, float cg, float cb, double cosI){
        if(radius<1) return;
        double safeCos=Math.max(0.08, cosI);
        int x0=Math.max(0,cx-radius), x1=Math.min(W-1,cx+radius);
        int y0=Math.max(0,cy-radius), y1=Math.min(H-1,cy+radius);
        float r2i=1f/(radius*radius+1f);
        for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++){
            float dx=x-cx, dy=(float)((y-cy)/safeCos);
            float d2=dx*dx+dy*dy; if(d2>radius*radius) continue;
            float f=(1f-d2*r2i); f=(float)Math.pow(f,2.5)*0.22f; // much more subtle falloff
            int idx=y*W+x, c=px[idx];
            px[idx]=(Math.min(255,((c>>16)&0xFF)+(int)(cr*f))<<16)
                   |(Math.min(255,((c>> 8)&0xFF)+(int)(cg*f))<<8)
                   | Math.min(255,((c    )&0xFF)+(int)(cb*f));
        }
    }

    /** Paint a soft glow strip along a spiral arm path */
    void armGlow(int cx, int cy, double sc, double cosI, int arms, double tightness,
                 float cr, float cg, float cb, float alpha){
        int steps=200;
        for(int s=0;s<steps;s++){
            double r=0.12+s*(1.4/steps);
            double ls=Math.log(r/0.05+1.0)*tightness;
            for(int arm=0;arm<arms;arm++){
                double ang=arm*(2*Math.PI/arms)+ls;
                int px2=(int)(cx+r*Math.cos(ang)*sc);
                int py2=(int)(cy+r*Math.sin(ang)*cosI*sc);
                if(px2<0||px2>=W||py2<0||py2>=H) continue;
                int rad=Math.min(60,(int)(sc*(0.035+r*0.02)));
                if(rad<2) continue;
                float fade=(float)(1-s/(double)steps)*alpha;
                nebula(px2,py2,rad,cr,cg,cb,fade*0.15f,false);
            }
        }
    }
    BufferedImage image() { return img; }
}

// ══ MAIN PANEL ══════════════════════════════════════════════════════

class SimPanel extends JPanel {
    static final int W=1280, H=860, CH=580;
    static final double AND_COS=0.225,AND_SIN=0.974,SOM_COS=0.105,SOM_SIN=0.995;

    // ── Mode & view ──
    private int    mode=0;
    private final double[] zoom={1,1,1,1,1,1,1}, panX={0,0,0,0,0,0,0}, panY={0,0,0,0,0,0,0};
    private final double[] rotX={0,0,0,0,0,0,0}, rotY={0,0,0,0,0,0,0};
    // ── Independent zoom/pan for each galaxy in Side-by-Side (mode 0) ──
    private double zoomA=1, zoomB=1;
    private double panXA=0, panYA=0, panXB=0, panYB=0;
    private double rotXA=0, rotYA=0, rotXB=0, rotYB=0;
    private boolean lastScrolledLeft=true; // tracks which galaxy was last zoomed
    private boolean mouseInLeftHalf(){ return hoverX < W/2; }

    // ── Mouse state ──
    private Point  drag0; private double dPX,dPY;
    private Point  rdrag0; private double rX0,rY0; // right-drag for 3D rotation
    private int    hoverX=-1, hoverY=-1;
    private String tipText=null; private int tipX,tipY;
    private final List<int[]>  hitPx =new ArrayList<>();
    private final List<String> hitTip=new ArrayList<>();

    // ── Speed ──
    private int    speedIdx=1;
    private static final double[] SPD={0.25,1.0,3.0,8.0};
    private static final String[] SPL={"SLOW","NORM","FAST","WARP"};
    private final Rectangle[] SRECT=new Rectangle[4];

    // ── Feature toggles ──
    private boolean showNebulae=true, showDarkMatter=false, showGravWell=false;
    private boolean placingStars=false, showHelp=false, paused=false;
    private boolean flythroughMode=false;
    private double  flyZ=0, flyVZ=0;
    private int     wavelength=0; // 0=vis,1=ir,2=xray,3=radio
    private static final String[] WL_NAMES={"VISIBLE","INFRARED","X-RAY","RADIO"};
    private static final Color[]  WL_COLS ={new Color(200,200,255),new Color(255,180,80),new Color(80,180,255),new Color(220,100,255)};

    // ── Supernovae ──
    private final List<Supernova> supernovae = new ArrayList<>();
    private double  novaTimer=0;

    // ── Stats ──
    private long    lastFpsTime=System.currentTimeMillis();
    private int     frameCount=0, fps=60;

    // ── GIF recording ──
    private boolean recording=false;
    private final List<BufferedImage> gifFrames=new ArrayList<>();
    private double  recTimer=0;
    private static final double REC_DUR=3.0;
    private String  recMsg=null; private long recMsgTime=0;

    // ── Screenshot ──
    private String  shotMsg=null; private long shotTime=0;

    // ── Galaxies ──
    private final Galaxy andGal=new Galaxy(), somGal=new Galaxy();
    private final Galaxy mwGal=new Galaxy();  // Milky Way
    private final Galaxy collA=new Galaxy(), collB=new Galaxy();
    private final Renderer ren=new Renderer(W,CH);
    private final BufferedImage bgImg;
    private final List<BGStar> bgStars=new ArrayList<>();
    // ── Fun facts ticker ──
    private int    factIdx=0;
    private double factTimer=0;
    private static final String[] FACTS={
        "The Milky Way and Andromeda will collide in ~4.5 billion years",
        "When galaxies collide, almost no stars actually hit each other",
        "The Sombrero galaxy has one of the most massive black holes known",
        "Gravitational waves from merging black holes travel at the speed of light",
        "A starburst galaxy can form stars 1000x faster than the Milky Way",
        "The Milky Way contains between 100-400 billion stars",
        "Andromeda is approaching us at 110 km/s — blueshift confirmed!",
        "After merging, Andromeda+Milky Way will form a giant elliptical galaxy",
        "Dark matter makes up ~85% of all matter in the universe",
        "The Sombrero's dust lane contains enough gas to form 1 billion new suns",
        "LIGO detected gravitational waves from a BH merger 1.3 billion ly away",
        "Spiral arms are density waves, not fixed structures — stars pass through them"
    };
    // ── Pulsars ──
    private double pulsarTimer=0;
    private final List<float[]> pulsars=new ArrayList<>(); // [wx,wy,phase,gid]
    // ── Shooting stars ──
    private double shootTimer=0;
    private final List<float[]> shootingStars=new ArrayList<>(); // [x,y,vx,vy,life,maxLife]
    private double collTime=0, collAX=0, collAY=0, collBX=0, collBY=0;
    // ── Collision phase system ──
    // Phase 0=approaching, 1=first pass+tidal, 2=separation, 3=second pass,
    //       4=final merge, 5=starburst, 6=elliptical forming, 7=BH inspiral,
    //       8=gravitational waves, 9=aftermath/quiet
    private int    collPhase=0;
    private double collPhaseTime=0;   // time within current phase
    private double starburstAlpha=0;  // starburst glow intensity
    private double bhSpiralAngle=0;   // BH orbit angle during inspiral
    private double bhSpiralR=0.4;     // BH separation during inspiral
    private double gwRipple=0;        // gravitational wave ripple radius
    private double ellipseProgress=0; // 0=two galaxies, 1=full ellipse
    private final List<float[]> starburstStars=new ArrayList<>(); // [x,y,vx,vy,life,r,g,b]

    private final String[] BTNS={
        "Side by Side","Andromeda M31","Sombrero M104","Milky Way",
        "⚡ Collision","📊 HR Diagram","📏 Scale Compare"
    };
    private final Rectangle[] BRECT=new Rectangle[7];
    private final Rectangle   lblRA=new Rectangle(), lblRS=new Rectangle();
    // Toolbar clickable button rects (populated during drawToolbar)
    private final Rectangle[] WLRECT  = new Rectangle[4]; // wavelength
    private final Rectangle   tbNebula  = new Rectangle();
    private final Rectangle   tbDarkMat = new Rectangle();
    private final Rectangle   tbGravWel = new Rectangle();
    private final Rectangle   tbPlace   = new Rectangle();
    private final Rectangle   tbShot    = new Rectangle();
    private final Rectangle   tbGif     = new Rectangle();
    private final Rectangle   tbFly     = new Rectangle();
    private final Random       rng=new Random();

    SimPanel(){
        setPreferredSize(new Dimension(W,H)); setBackground(Color.BLACK);

        Random r0=new Random(2025);
        andGal.name="Andromeda M31"; andGal.labelCol=new Color(170,212,255); andGal.build(8000,2,false,0,r0);
        somGal.name="Sombrero M104"; somGal.labelCol=new Color(255,200,100); somGal.build(6000,0,true,1,r0);
        mwGal.name="Milky Way";      mwGal.labelCol=new Color(180,255,180);  mwGal.build(7000,4,false,3,r0);
        Random cr=new Random(99);
        collA.name="Andromeda M31"; collA.labelCol=new Color(130,165,255); collA.build(2500,2,false,0,cr);
        collB.name="Sombrero M104"; collB.labelCol=new Color(255,200,100); collB.build(2500,0,true,1,cr);
        // Seed pulsars — place ~8 in each spiral galaxy
        Random pr=new Random(555);
        for(Galaxy g:new Galaxy[]{andGal,mwGal}){
            for(int i=0;i<8;i++){
                Star s=g.stars.get(pr.nextInt(g.stars.size()));
                pulsars.add(new float[]{(float)(s.r*Math.cos(s.angle)),(float)(s.r*Math.sin(s.angle)),pr.nextFloat()*6.28f,(float)(g==mwGal?3:0)});
            }
        }

        bgImg=buildBg(new Random(42)); buildBGStars(new Random(777));

        new Timer(16, e -> {
            if(paused) return;
            double dt=0.018*SPD[speedIdx];
            bgStars.forEach(s->s.tick((float)(0.016*SPD[speedIdx])));
            andGal.tick(dt); somGal.tick(dt); mwGal.tick(dt); collA.tick(dt); collB.tick(dt);
            collTime += 0.016*0.042*SPD[speedIdx];
            // Advance collision phase
            if(mode==4) tickCollisionPhase(0.016*SPD[speedIdx]);
            // Fun facts
            factTimer+=0.016; if(factTimer>7){ factTimer=0; factIdx=(factIdx+1)%FACTS.length; }
            // Pulsars
            pulsarTimer+=0.016*SPD[speedIdx];
            for(float[]p:pulsars) p[2]+=0.016f*SPD[speedIdx]*3.5f;
            // Shooting stars
            shootTimer+=0.016;
            if(shootTimer>4+rng.nextDouble()*6){ shootTimer=0;
                float sx=rng.nextFloat()*W, sy=rng.nextFloat()*(CH*0.6f)+76;
                float ang=(float)(Math.PI*0.2+rng.nextDouble()*0.4);
                float spd=180+rng.nextFloat()*120;
                float life=0.6f+rng.nextFloat()*0.8f;
                shootingStars.add(new float[]{sx,sy,(float)Math.cos(ang)*spd,(float)Math.sin(ang)*spd,life,life});
            }
            shootingStars.removeIf(s->{s[0]+=s[2]*0.016f;s[1]+=s[3]*0.016f;s[4]-=0.016f;return s[4]<=0||s[0]<0||s[0]>W||s[1]<76||s[1]>CH+76;});
            // Supernovae
            novaTimer += dt;
            if(novaTimer>18/SPD[speedIdx]){ spawnSupernova(); novaTimer=0; }
            supernovae.removeIf(sn->{sn.tick(dt); return sn.dead;});
            // Flythrough
            if(flythroughMode){ flyZ+=flyVZ*dt; flyVZ=Math.min(flyVZ+0.8*dt,4.0); if(flyZ>8.0) flyZ=0; }
            // FPS
            frameCount++;
            long now=System.currentTimeMillis();
            if(now-lastFpsTime>=1000){ fps=frameCount; frameCount=0; lastFpsTime=now; }
            // GIF recording
            if(recording){ recTimer+=0.016; if(recTimer<=REC_DUR) captureGifFrame(); else finaliseGif(); }
            repaint();
        }).start();

        setupInput();
    }

    private void setupInput(){
        setFocusable(true);
        addKeyListener(new KeyAdapter(){
            @Override public void keyPressed(KeyEvent e){
                switch(e.getKeyChar()){
                    case ' ' -> paused=!paused;
                    case 'f','F' -> { flythroughMode=!flythroughMode; flyVZ=0; flyZ=0; }
                    case 'h','H' -> showHelp=!showHelp;
                    case 'n','N' -> showNebulae=!showNebulae;
                    case 'd','D' -> showDarkMatter=!showDarkMatter;
                    case 'g','G' -> showGravWell=!showGravWell;
                    case 'p','P' -> placingStars=!placingStars;
                    case 's','S' -> takeScreenshot();
                    case 'r','R' -> toggleRecord();
                    case '1' -> mode=0; case '2' -> mode=1; case '3' -> mode=2;
                    case '4' -> mode=3;
                    case '5' -> { mode=4; collPhase=0; collPhaseTime=0; collTime=0; starburstStars.clear(); bhSpiralR=0.4; ellipseProgress=0; gwRipple=0; }
                    case '6' -> mode=5; case '7' -> mode=6;
                    case 'v','V' -> wavelength=0; case 'i','I' -> wavelength=1;
                    case 'x','X' -> wavelength=2; case 'q','Q' -> wavelength=3;
                }
                switch(e.getKeyCode()){
                    case KeyEvent.VK_EQUALS,KeyEvent.VK_PLUS  -> zoom[mode]=Math.min(8.0,zoom[mode]*1.2);
                    case KeyEvent.VK_MINUS                    -> zoom[mode]=Math.max(0.2,zoom[mode]/1.2);
                    case KeyEvent.VK_LEFT  -> panX[mode]+=30;
                    case KeyEvent.VK_RIGHT -> panX[mode]-=30;
                    case KeyEvent.VK_UP    -> panY[mode]+=30;
                    case KeyEvent.VK_DOWN  -> panY[mode]-=30;
                }
            }
        });
        addMouseWheelListener(e -> {
            double f=e.getWheelRotation()<0?1.15:0.87;
            if(mode==0){
                if(mouseInLeftHalf()){
                    lastScrolledLeft=true;
                    zoomA=Math.max(1.0,Math.min(8.0,zoomA*f)); // min 1.0 snaps back to side-by-side
                } else {
                    lastScrolledLeft=false;
                    zoomB=Math.max(1.0,Math.min(8.0,zoomB*f));
                }
                // When either zooms back to 1.0, reset both cleanly
                if(zoomA<=1.0) { zoomA=1.0; panXA=0; panYA=0; }
                if(zoomB<=1.0) { zoomB=1.0; panXB=0; panYB=0; }
            } else {
                double old=zoom[mode]; zoom[mode]=Math.max(0.2,Math.min(8.0,zoom[mode]*f));
                panX[mode]*=zoom[mode]/old; panY[mode]*=zoom[mode]/old;
            }
        });
        addMouseListener(new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){
                requestFocusInWindow();
                Point pt = e.getPoint();
                // Mode buttons
                for(int i=0;i<BRECT.length;i++) if(BRECT[i]!=null&&BRECT[i].contains(pt)){
                    mode=i;
                    if(i==4){ collPhase=0; collPhaseTime=0; collTime=0; starburstStars.clear(); bhSpiralR=0.4; ellipseProgress=0; gwRipple=0; }
                    return;
                }
                // Speed buttons
                for(int i=0;i<SRECT.length;i++) if(SRECT[i]!=null&&SRECT[i].contains(pt)){speedIdx=i;return;}
                // Wavelength buttons
                for(int i=0;i<WLRECT.length;i++) if(WLRECT[i]!=null&&WLRECT[i].contains(pt)){wavelength=i;return;}
                // Toggle buttons
                if(tbNebula.contains(pt)){showNebulae=!showNebulae;return;}
                if(tbDarkMat.contains(pt)){showDarkMatter=!showDarkMatter;return;}
                if(tbGravWel.contains(pt)){showGravWell=!showGravWell;return;}
                if(tbPlace.contains(pt)){placingStars=!placingStars;return;}
                if(tbShot.contains(pt)){takeScreenshot();return;}
                if(tbGif.contains(pt)){toggleRecord();return;}
                if(tbFly.contains(pt)){flythroughMode=!flythroughMode;flyVZ=0;flyZ=0;return;}
                // Right click → 3D rotation
                if(SwingUtilities.isRightMouseButton(e)){
                    rdrag0=e.getPoint();
                    if(mode==0){
                        rX0=mouseInLeftHalf()?rotXA:rotXB;
                        rY0=mouseInLeftHalf()?rotYA:rotYB;
                    } else { rX0=rotX[mode]; rY0=rotY[mode]; }
                    return;
                }
                // Left click → place star or pan
                if(placingStars && e.getY()<CH+76){ placeStarAt(e.getX(), e.getY()); return; }
                if(mode==0){
                    drag0=e.getPoint();
                    dPX=mouseInLeftHalf()?panXA:panXB;
                    dPY=mouseInLeftHalf()?panYA:panYB;
                } else {
                    drag0=e.getPoint(); dPX=panX[mode]; dPY=panY[mode];
                }
            }
            @Override public void mouseReleased(MouseEvent e){ drag0=null; rdrag0=null; }
            @Override public void mouseClicked(MouseEvent e){
                if(e.getClickCount()==2&&!SwingUtilities.isRightMouseButton(e)){
                    if(mode==0){
                        if(mouseInLeftHalf()){zoomA=1;panXA=0;panYA=0;rotXA=0;rotYA=0;}
                        else{zoomB=1;panXB=0;panYB=0;rotXB=0;rotYB=0;}
                    } else {zoom[mode]=1;panX[mode]=0;panY[mode]=0;rotX[mode]=0;rotY[mode]=0;}
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter(){
            @Override public void mouseDragged(MouseEvent e){
                if(rdrag0!=null){
                    if(mode==0){
                        if(mouseInLeftHalf()){rotYA=rY0+(e.getX()-rdrag0.x)*0.008;rotXA=rX0+(e.getY()-rdrag0.y)*0.008;}
                        else{rotYB=rY0+(e.getX()-rdrag0.x)*0.008;rotXB=rX0+(e.getY()-rdrag0.y)*0.008;}
                    } else { rotY[mode]=rY0+(e.getX()-rdrag0.x)*0.008; rotX[mode]=rX0+(e.getY()-rdrag0.y)*0.008; }
                    return;
                }
                if(drag0==null) return;
                if(mode==0){
                    if(mouseInLeftHalf()){panXA=dPX+(e.getX()-drag0.x);panYA=dPY+(e.getY()-drag0.y);}
                    else{panXB=dPX+(e.getX()-drag0.x);panYB=dPY+(e.getY()-drag0.y);}
                } else { panX[mode]=dPX+(e.getX()-drag0.x); panY[mode]=dPY+(e.getY()-drag0.y); }
            }
            @Override public void mouseMoved(MouseEvent e){
                hoverX=e.getX(); hoverY=e.getY(); tipText=null;
                if(lblRA.contains(hoverX,hoverY)){tipText="Andromeda M31\n2.537 Million ly away\n220,000 ly diameter\n~1 Trillion stars";tipX=hoverX+14;tipY=hoverY-8;}
                else if(lblRS.contains(hoverX,hoverY)){tipText="Sombrero M104\n28 Million ly away\n50,000 ly diameter\nBlack hole: ~1 Billion M\u2609";tipX=hoverX+14;tipY=hoverY-8;}
                else for(int i=0;i<hitPx.size();i++){int[]h=hitPx.get(i);int dx=hoverX-h[0],dy=hoverY-h[1];if(dx*dx+dy*dy<=h[2]*h[2]){tipText=hitTip.get(i);tipX=hoverX+14;tipY=hoverY-8;break;}}
                boolean onB=false; for(Rectangle r:BRECT) if(r!=null&&r.contains(hoverX,hoverY)){onB=true;break;}
                for(Rectangle r:SRECT) if(r!=null&&r.contains(hoverX,hoverY)){onB=true;break;}
                if(placingStars&&hoverY<CH) onB=true;
                setCursor(onB?Cursor.getPredefinedCursor(Cursor.HAND_CURSOR):Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            }
        });
    }

    private void spawnSupernova(){
        // Pick a random supergiant-type star from current view galaxy
        Galaxy g = (mode==2||mode==3)?somGal:andGal;
        List<Star> candidates=new ArrayList<>();
        for(Star s:g.stars) if(s.isSupergiant||s.glow>=3) candidates.add(s);
        if(candidates.isEmpty()) return;
        Star s=candidates.get(rng.nextInt(candidates.size()));
        supernovae.add(new Supernova(s.r*Math.cos(s.angle), s.r*Math.sin(s.angle)));
    }

    private void placeStarAt(int sx, int sy){
        // Convert screen coords to sim coords — correct transform
        int cx=galaxyCX()+(int)panX[mode];
        int cy=CH/2+(int)panY[mode];
        double sc=120.0*zoom[mode];
        double wx=(sx-cx)/sc, wy=(sy-cy)/sc;
        Star s=new Star();
        s.r=Math.sqrt(wx*wx+wy*wy); s.angle=Math.atan2(wy,wx);
        s.omega=1.0/Math.max(s.r,0.08); s.diskZ=0f;
        s.cr=100+rng.nextInt(155); s.cg=100+rng.nextInt(155); s.cb=200+rng.nextInt(55);
        s.baseCr=s.cr; s.baseCg=s.cg; s.baseCb=s.cb;
        s.glow=2; s.tip="Custom Star (you placed this!)";
        s.twPhase=rng.nextFloat()*6.28f; s.twSpeed=0.5f+rng.nextFloat();
        Galaxy g=(mode==2)?somGal:andGal;
        g.stars.add(s);
    }

    private int galaxyCX(){ return mode==0?W/4:W/2; }

    // ── Screenshot ──────────────────────────────────────────────
    private void takeScreenshot(){
        try{
            BufferedImage shot=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
            Graphics2D sg=shot.createGraphics(); paintComponent(sg); sg.dispose();
            String home=System.getProperty("user.home");
            String ts=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File f=new File(home+File.separator+"Desktop"+File.separator+"Galaxy_"+ts+".png");
            ImageIO.write(shot,"PNG",f);
            shotMsg="✓ Saved: "+f.getName(); shotTime=System.currentTimeMillis();
        }catch(Exception ex){shotMsg="✗ "+ex.getMessage(); shotTime=System.currentTimeMillis();}
    }

    // ── GIF Recording ────────────────────────────────────────────
    private void toggleRecord(){
        if(recording){ finaliseGif(); return; }
        gifFrames.clear(); recTimer=0; recording=true; recMsg="● REC"; recMsgTime=System.currentTimeMillis();
    }

    private void captureGifFrame(){
        BufferedImage frame=new BufferedImage(W,CH,BufferedImage.TYPE_INT_RGB);
        Graphics2D fg=frame.createGraphics(); paintComponent(fg); fg.dispose();
        gifFrames.add(frame);
    }

    private void finaliseGif(){
        recording=false;
        if(gifFrames.isEmpty()){recMsg="✗ No frames"; recMsgTime=System.currentTimeMillis(); return;}
        new Thread(()->{
            List<BufferedImage> frames = new ArrayList<>(gifFrames); // snapshot — thread-safe copy
            gifFrames.clear();
            try{
                String home=System.getProperty("user.home");
                String ts=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                File f=new File(home+File.separator+"Desktop"+File.separator+"Galaxy_"+ts+".gif");
                ImageWriter writer=ImageIO.getImageWritersByFormatName("gif").next();
                ImageOutputStream ios=ImageIO.createImageOutputStream(f);
                writer.setOutput(ios);
                writer.prepareWriteSequence(null);
                for(int i=0;i<frames.size();i++){
                    BufferedImage frame=frames.get(i);
                    // Scale down for GIF (half size)
                    BufferedImage small=new BufferedImage(W/2,CH/2,BufferedImage.TYPE_INT_RGB);
                    Graphics2D sg=small.createGraphics();
                    sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    sg.drawImage(frame,0,0,W/2,CH/2,null); sg.dispose();
                    IIOMetadata meta=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(small),null);
                    String fmt=meta.getNativeMetadataFormatName();
                    IIOMetadataNode root=(IIOMetadataNode)meta.getAsTree(fmt);
                    IIOMetadataNode gce=new IIOMetadataNode("GraphicControlExtension");
                    gce.setAttribute("disposalMethod","doNotDispose"); gce.setAttribute("userInputFlag","FALSE");
                    gce.setAttribute("transparentColorFlag","FALSE"); gce.setAttribute("delayTime","5");
                    gce.setAttribute("transparentColorIndex","0"); root.insertBefore(gce,root.getFirstChild());
                    if(i==0){
                        IIOMetadataNode appExts=new IIOMetadataNode("ApplicationExtensions");
                        IIOMetadataNode appExt=new IIOMetadataNode("ApplicationExtension");
                        appExt.setAttribute("applicationID","NETSCAPE"); appExt.setAttribute("authenticationCode","2.0");
                        appExt.setUserObject(new byte[]{0x1,(byte)0,(byte)0}); appExts.appendChild(appExt); root.appendChild(appExts);
                    }
                    meta.setFromTree(fmt,root);
                    writer.writeToSequence(new IIOImage(small,null,meta),null);
                }
                writer.endWriteSequence(); ios.close(); writer.dispose();
                recMsg="✓ GIF saved: "+f.getName(); recMsgTime=System.currentTimeMillis();
            }catch(Exception ex){recMsg="✗ GIF failed: "+ex.getMessage(); recMsgTime=System.currentTimeMillis();}
        }).start();
    }

    // ── PAINT ────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics gr){
        Graphics2D g2=(Graphics2D)gr;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(Color.BLACK); g2.fillRect(0,0,W,H);
        if(mode!=5&&mode!=6){
            g2.drawImage(bgImg,0,76,null);
            // Animated background stars
            for(BGStar s:bgStars){
                if(s.y>=CH) continue;
                float a=s.alpha(); int ia=(int)(a*255); if(ia<=0) continue;
                g2.setColor(new Color(Math.min(255,s.rc*ia/255),Math.min(255,s.gc*ia/255),Math.min(255,s.bc*ia/255),ia));
                g2.fillRect((int)s.x,(int)s.y+76,1,1);
                if(s.size==1&&ia>80){int ha=ia/5;g2.setColor(new Color(Math.min(255,s.rc),Math.min(255,s.gc),Math.min(255,s.bc),ha));g2.fillRect((int)s.x-1,(int)s.y+76,1,1);g2.fillRect((int)s.x+1,(int)s.y+76,1,1);g2.fillRect((int)s.x,(int)s.y+75,1,1);g2.fillRect((int)s.x,(int)s.y+77,1,1);}
            }
        }

        ren.clear(); hitPx.clear(); hitTip.clear();

        if(flythroughMode){
            paintFlythrough(g2);
        } else {
            double sc=120.0*zoom[mode];
            switch(mode){
                case 0 -> {
                    // ── Simple correct Side-by-Side zoom ──
                    // lastScrolledLeft tells us which galaxy is being zoomed
                    // At zoom 1.0: side by side. Zooming in: other galaxy fades.
                    double scA=120.0*zoomA, scB=120.0*zoomB;
                    int andCX=W/4+(int)panXA, andCY=CH/2+(int)panYA;
                    int somCX=3*W/4+(int)panXB, somCY=CH/2+(int)panYB;

                    // fade = how much the OTHER galaxy should be visible (1=fully visible, 0=gone)
                    // starts fading when zoom > 1.2, gone by zoom 2.2
                    double fadeOther;
                    if(lastScrolledLeft){
                        fadeOther=Math.max(0,Math.min(1,(2.2-zoomA)/1.0));
                    } else {
                        fadeOther=Math.max(0,Math.min(1,(2.2-zoomB)/1.0));
                    }

                    if(lastScrolledLeft){
                        // Andromeda is zoomed — draw Sombrero first then Andromeda on top
                        if(zoomB>1.0||fadeOther>0.01){
                            paintGalaxy(somGal,0,0,somCX,somCY,scB,SOM_COS,SOM_SIN,true,rotXB,rotYB);
                        }
                        paintGalaxy(andGal,0,0,andCX,andCY,scA,AND_COS,AND_SIN,false,rotXA,rotYA);
                        // Fade Sombrero by darkening its half of the buffer
                        if(fadeOther<0.99){
                            int[]buf=((DataBufferInt)ren.image().getRaster().getDataBuffer()).getData();
                            float fade=(float)fadeOther;
                            for(int py=0;py<CH;py++) for(int bx=W/2;bx<W;bx++){
                                int idx=py*W+bx, c=buf[idx];
                                buf[idx]=((int)(((c>>16)&0xFF)*fade)<<16)|((int)(((c>>8)&0xFF)*fade)<<8)|(int)((c&0xFF)*fade);
                            }
                        }
                    } else {
                        // Sombrero is zoomed — draw Andromeda first then Sombrero on top
                        if(zoomA>1.0||fadeOther>0.01){
                            paintGalaxy(andGal,0,0,andCX,andCY,scA,AND_COS,AND_SIN,false,rotXA,rotYA);
                        }
                        paintGalaxy(somGal,0,0,somCX,somCY,scB,SOM_COS,SOM_SIN,true,rotXB,rotYB);
                        // Fade Andromeda by darkening its half of the buffer
                        if(fadeOther<0.99){
                            int[]buf=((DataBufferInt)ren.image().getRaster().getDataBuffer()).getData();
                            float fade=(float)fadeOther;
                            for(int py=0;py<CH;py++) for(int bx=0;bx<W/2;bx++){
                                int idx=py*W+bx, c=buf[idx];
                                buf[idx]=((int)(((c>>16)&0xFF)*fade)<<16)|((int)(((c>>8)&0xFF)*fade)<<8)|(int)((c&0xFF)*fade);
                            }
                        }
                    }
                }
                case 1 -> paintGalaxy(andGal,0,0,W/2+(int)panX[1],CH/2+(int)panY[1],sc,AND_COS,AND_SIN,false,rotX[1],rotY[1]);
                case 2 -> paintGalaxy(somGal,0,0,W/2+(int)panX[2],CH/2+(int)panY[2],sc,SOM_COS,SOM_SIN,true,rotX[2],rotY[2]);
                case 3 -> paintGalaxy(mwGal,0,0,W/2+(int)panX[3],CH/2+(int)panY[3],sc,0.18,0.984,false,rotX[3],rotY[3]);
                case 4 -> paintCollision(W/2+(int)panX[4],CH/2+(int)panY[4],120.0*zoom[4]);
                case 5 -> paintHRDiagram(g2);
                case 6 -> paintScaleCompare(g2);
            }
            if(mode!=5&&mode!=6){
                // Supernovae
                int cx=W/2+(int)panX[mode],cy=CH/2+(int)panY[mode]; double sc2=120.0*zoom[mode];
                for(Supernova sn:supernovae){
                    int snx=(int)(cx+sn.x*sc2), sny=(int)(cy+sn.y*sc2*(mode==2?SOM_COS:AND_COS));
                    ren.supernova(snx,sny,sn.radius*(float)sc2*0.35f,sn.alpha,sn.cr,sn.cg,sn.cb);
                }
                // Gravitational lensing
                if(mode!=4 && zoom[mode]>2.5){
                    int bhX=galaxyCX()+(int)panX[mode], bhY=CH/2+(int)panY[mode];
                    ren.gravLens(bhX,bhY,Math.max(4,(int)((zoom[mode]-2.5)*12+6)));
                }
                // Gravity well grid
                if(showGravWell){
                    int gx=W/2+(int)panX[mode], gy=CH/2+(int)panY[mode];
                    ren.gravityWellGrid(gx,gy,120.0*zoom[mode],mode==2?SOM_COS:AND_COS);
                }
                // Pulsars
                if(mode<4){
                    Galaxy pg=(mode==2)?somGal:(mode==3)?mwGal:andGal;
                    int pcx=W/2+(int)panX[mode], pcy=CH/2+(int)panY[mode];
                    double psc=120.0*zoom[mode], pcosI=(mode==2)?SOM_COS:(mode==3)?0.18:AND_COS;
                    for(float[]p:pulsars){
                        if((int)p[3]!=(mode==2?1:mode==3?3:0)) continue;
                        float pulse=(float)(0.5+0.5*Math.sin(p[2]*4));
                        int px2=(int)(pcx+p[0]*psc), py2=(int)(pcy+p[1]*psc*pcosI);
                        if(pulse>0.7f) for(int r=3;r>=0;r--) ren.dot(px2,py2,(int)(80*pulse),(int)(200*pulse),(int)(255*pulse),r);
                    }
                }
            }
        }
        float activeZoom=(float)(mode==0?Math.max(zoomA,zoomB):zoom[mode]);
        float bloomThr = Math.min(80f, 22f + (activeZoom-1)*22f);
        if(mode!=5&&mode!=6){
            ren.bloom(0.35f,3,2,flythroughMode?999f:bloomThr);
            ren.applyWavelength(wavelength);
            g2.drawImage(ren.image(),0,76,null);
        }
        // Shooting stars
        for(float[]ss:shootingStars){
            float life=ss[4]/ss[5]; int alpha=(int)(life*200);
            float len=Math.min(60,ss[5]*80*life);
            g2.setColor(new Color(220,230,255,alpha));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine((int)ss[0],(int)ss[1],(int)(ss[0]-ss[2]*0.016f*4),(int)(ss[1]-ss[3]*0.016f*4));
            g2.setStroke(new BasicStroke(1f));
        }

        // Supernova flash overlay
        for(Supernova sn:supernovae) if(sn.radius<sn.maxR*0.15f&&sn.alpha>0.7f){
            g2.setColor(new Color(255,240,200,(int)(sn.alpha*30))); g2.fillRect(0,0,W,CH+76);
        }
        // Gravitational wave ripple overlay (phase 8)
        if(mode==4 && collPhase==8 && gwRipple>0){
            int gcx=W/2+(int)panX[4], gcy=CH/2+76+(int)panY[4];
            double sc3=120.0*zoom[4];
            for(int ring=0;ring<3;ring++){
                double rr=(gwRipple-ring*0.9); if(rr<=0) continue;
                int rad=(int)(rr*sc3*0.5); if(rad<2) continue;
                float alpha=(float)(Math.max(0,1-rr/3.5)*0.6*(1-ring*0.25));
                g2.setColor(new Color(180,220,255,(int)(alpha*200)));
                g2.setStroke(new BasicStroke(2.5f-ring*0.6f));
                g2.drawOval(gcx-rad,gcy-rad/3,rad*2,rad*2/3);
            }
            g2.setStroke(new BasicStroke(1f));
        }
        // Starburst screen flash (phase 5)
        if(mode==4 && collPhase==5 && collPhaseTime<1.5){
            float f=(float)Math.max(0,1-collPhaseTime/1.5);
            g2.setColor(new Color(80,120,255,(int)(f*60))); g2.fillRect(0,76,W,CH);
        }
        // Fun facts ticker (bottom of canvas)
        if(mode!=5&&mode!=6){
            String fact=FACTS[factIdx];
            g2.setFont(new Font("Courier New",Font.PLAIN,9));
            FontMetrics fmf=g2.getFontMetrics();
            int fw=fmf.stringWidth(fact)+16;
            g2.setColor(new Color(5,12,30,180)); g2.fillRoundRect(W/2-fw/2,CH+44,fw,14,6,6);
            float pulse=(float)(0.6+0.4*Math.sin(factTimer*2));
            g2.setColor(new Color(80,140,200,(int)(pulse*200)));
            g2.drawString("✦ "+fact+" ✦",W/2-fmf.stringWidth("✦ "+fact+" ✦")/2,CH+54);
        }
        // Star type legend (modes 1-3)
        if(mode>=1&&mode<=3) drawStarLegend(g2);

        drawButtons(g2);   // always on top — drawn first so nothing overlaps
        drawOverlay(g2);
        drawHUD(g2);
        drawToolbar(g2);
        drawLiveStats(g2);
        drawTooltip(g2);
        drawMessages(g2);
        if(showHelp) drawHelp(g2);
        if(placingStars) drawPlacingHint(g2);
        if(recording) drawRecordingBar(g2);
    }

    // ── 3D PROJECTION ────────────────────────────────────────────
    private int[] project3D(double lx,double ly_screen,double lz_screen,
                             int ocx,int ocy,double sc,double rx,double ry){
        // Full 3D: apply user rotation on top of inclination
        // Current positions already have inclination baked in as (lx, ly_proj)
        // We treat ly_proj as Y, compute approximate Z from the removed component
        double x=lx, y=ly_screen, z=lz_screen;
        // Apply yaw (around Y axis)
        double x2= x*Math.cos(ry)+z*Math.sin(ry);
        double z2=-x*Math.sin(ry)+z*Math.cos(ry);
        // Apply pitch (around X axis)
        double y3= y*Math.cos(rx)-z2*Math.sin(rx);
        return new int[]{(int)(ocx+x2*sc),(int)(ocy+y3*sc)};
    }

    // ── FLYTHROUGH ───────────────────────────────────────────────
    private void paintFlythrough(Graphics2D g2){
        // Dark space backdrop
        g2.setColor(new Color(0,0,8)); g2.fillRect(0,0,W,CH);
        Galaxy gal=(mode==2)?somGal:andGal;
        double cosI=(mode==2)?SOM_COS:AND_COS, sinI=(mode==2)?SOM_SIN:AND_SIN;
        double focal=500.0;
        int cx=W/2,cy=CH/2;
        for(Star s:gal.stars){
            double wx=s.r*Math.cos(s.angle);
            double wy=s.r*Math.sin(s.angle)*cosI+s.diskZ*sinI;
            double wz=-s.r*Math.sin(s.angle)*sinI+s.diskZ*cosI - flyZ;
            if(wz<0.05) continue;
            double scale=focal/wz;
            int sx=(int)(cx+wx*scale*30), sy=(int)(cy+wy*scale*30);
            float a=s.alpha();
            int ki=Math.min(4,(int)(4.0/(1+wz*0.5)));
            ren.dot(sx,sy,s.cr*a,s.cg*a,s.cb*a,ki);
        }
        // Draw warp streaks
        for(BGStar bs:bgStars){
            float streakLen=(float)(flyVZ*8);
            g2.setColor(new Color(Math.min(255,bs.rc),Math.min(255,bs.gc),Math.min(255,bs.bc),(int)(bs.alpha()*80)));
            g2.drawLine((int)bs.x,(int)bs.y,(int)(cx+(bs.x-cx)*1.05f),(int)(cy+(bs.y-cy)*1.05f));
        }
        g2.setFont(new Font("Courier New",Font.BOLD,14));
        g2.setColor(new Color(100,180,255,180));
        g2.drawString("WARP FLYTHROUGH  — Press F to exit  — Use speed buttons to control",W/2-300,CH-20);
        // Composite renderer buffer so flythrough stars are actually visible
        ren.bloom(0.45f,2,2,30f);
        g2.drawImage(ren.image(),0,0,null);
    }

    // ── PAINT GALAXY ─────────────────────────────────────────────
    private void paintGalaxy(Galaxy gal,double gOX,double gOY,int ocx,int ocy,double sc,
                              double cosI,double sinI,boolean dust,double rx,double ry){
        if(showDarkMatter) ren.darkMatterHalo(ocx,ocy,(int)(sc*2.8));

        // ── Smooth core glow — integrated light from dense core ──
        int gid=gal.stars.isEmpty()?0:gal.stars.get(0).gid;
        int capR=Math.min((int)(sc*0.9), 280);
        if(gid==0){      // Andromeda: subtle warm amber glow
            ren.galaxyCore(ocx,ocy,Math.min((int)(sc*0.40),capR),180,100,35,cosI);
            ren.galaxyCore(ocx,ocy,Math.min((int)(sc*0.18),capR),255,160,60,cosI);
        } else if(gid==1){ // Sombrero: flat edge-on white-yellow core
            ren.galaxyCore(ocx,ocy,Math.min((int)(sc*0.55),capR),160,110,50,0.25);
            ren.galaxyCore(ocx,ocy,Math.min((int)(sc*0.28),capR),240,185,100,0.25);
            ren.galaxyCore(ocx,ocy,Math.min((int)(sc*0.10),capR),255,230,170,0.25);
        } else if(gid==3){ // Milky Way: yellow bar
            ren.galaxyCore(ocx,ocy,Math.min((int)(sc*0.35),capR),170,115,40,cosI);
            ren.galaxyCore(ocx,ocy,Math.min((int)(sc*0.15),capR),240,175,80,cosI);
        } else {
            ren.galaxyCore(ocx,ocy,Math.min((int)(sc*0.30),capR),160,110,40,cosI);
        }

        // ── Arm glow — soft coloured mist along spiral arms ──
        int arms=gid==3?4:gid==0?2:0;
        if(arms>0&&showNebulae){
            double tight=gid==3?3.0:2.5;
            if(gid==0){ // Andromeda: blue-white arm glow
                ren.armGlow(ocx,ocy,sc,cosI,arms,tight,80,120,255,1.0f);
            } else if(gid==3){ // Milky Way: mixed arm glow
                ren.armGlow(ocx,ocy,sc,cosI,arms,tight,60,100,255,0.9f);
            }
        }

        // ── Nebulae ──
        if(showNebulae){
            for(NebulaCloud n:gal.nebulae){
                double nx=gOX+n.r*Math.cos(n.angle), ny=gOY+n.r*Math.sin(n.angle)*cosI;
                double drift=0.012*Math.sin(n.driftPhase);
                int[] sp=project3D(nx+drift,ny+drift*0.5,0,ocx,ocy,sc,rx,ry);
                ren.nebula(sp[0],sp[1],(int)(n.size*sc*0.85),n.cr,n.cg,n.cb,n.alpha+(float)Math.sin(n.driftPhase)*0.03f,n.dark);
            }
        }

        // ── Individual stars ──
        for(Star s:gal.stars){
            double lx=s.r*Math.cos(s.angle), lyI=s.r*Math.sin(s.angle)*cosI+s.diskZ*sinI;
            double lzI=-s.r*Math.sin(s.angle)*sinI+s.diskZ*cosI;
            int[] sp=project3D(gOX+lx,gOY+lyI,lzI,ocx,ocy,sc,rx,ry);
            float a=s.alpha();
            ren.dot(sp[0],sp[1],s.cr*a,s.cg*a,s.cb*a,Math.min(4,s.glow));
            // Diffraction spikes on bright stars
            if(s.glow>=2&&sp[0]>0&&sp[0]<W&&sp[1]>0&&sp[1]<CH){
                float sk=s.cr*a*0.4f, sgk=s.cg*a*0.4f, sbk=s.cb*a*0.4f;
                int sl=s.glow>=3?6:3;
                for(int d=1;d<=sl;d++){
                    float fade=1f-d/(float)(sl+1);
                    ren.dot(sp[0]+d,sp[1],sk*fade,sgk*fade,sbk*fade,0);
                    ren.dot(sp[0]-d,sp[1],sk*fade,sgk*fade,sbk*fade,0);
                    ren.dot(sp[0],sp[1]+d,sk*fade,sgk*fade,sbk*fade,0);
                    ren.dot(sp[0],sp[1]-d,sk*fade,sgk*fade,sbk*fade,0);
                }
            }
            if(s.glow>=3&&sp[0]>=0&&sp[0]<W&&sp[1]>=0&&sp[1]<CH){
                hitPx.add(new int[]{sp[0],sp[1],(int)(Renderer.GR[Math.min(4,s.glow)]*3.5)});
                hitTip.add((s.tip!=null?s.tip:"Star")+"\n"+gal.name);
            }
        }

        // ── Dust lane for edge-on galaxies ──
        if(dust){
            int hw=(int)(1.4*sc*0.96), hh=Math.max(3,(int)(1.4*sc*0.048));
            ren.dustLane(ocx+2,ocy+1,hw,hh);
            // Extra thick dust for Sombrero
            if(gid==1){ ren.dustLane(ocx,ocy,hw,(int)(hh*1.8)); }
        }
    }

    // ── COLLISION PHASE TICKER ───────────────────────────────────
    private void tickCollisionPhase(double dt){
        collPhaseTime += dt;
        switch(collPhase){
            case 0 -> { if(collPhaseTime>18) nextPhase(); } // approaching
            case 1 -> { if(collPhaseTime>14) nextPhase(); } // first pass & tidal streams
            case 2 -> { if(collPhaseTime>12) nextPhase(); } // separation
            case 3 -> { if(collPhaseTime>12) nextPhase(); } // second pass closer
            case 4 -> { if(collPhaseTime>10) nextPhase(); } // final merge flash
            case 5 -> { // starburst — spawn bright new blue stars
                starburstAlpha = Math.min(1.0, collPhaseTime*0.4);
                if(collPhaseTime<8 && rng.nextDouble()<0.18*dt*60){
                    float ang=(float)(rng.nextDouble()*Math.PI*2);
                    float spd=0.3f+rng.nextFloat()*1.2f;
                    starburstStars.add(new float[]{0,0,
                        (float)Math.cos(ang)*spd,(float)Math.sin(ang)*spd,
                        3f+rng.nextFloat()*4f,
                        100+rng.nextInt(100),180+rng.nextInt(75),255});
                }
                for(float[] bs:starburstStars){ bs[0]+=bs[2]*(float)dt; bs[1]+=bs[3]*(float)dt; bs[4]-=(float)dt; }
                starburstStars.removeIf(bs->bs[4]<=0);
                if(collPhaseTime>10) nextPhase();
            }
            case 6 -> { // elliptical forming
                ellipseProgress = Math.min(1.0, collPhaseTime/8.0);
                if(collPhaseTime>10) nextPhase();
            }
            case 7 -> { // BH inspiral
                bhSpiralAngle += dt*1.8*(1+collPhaseTime*0.15);
                bhSpiralR = Math.max(0.01, 0.4-collPhaseTime*0.038);
                if(collPhaseTime>10) nextPhase();
            }
            case 8 -> { // gravitational waves
                gwRipple += dt*0.9;
                if(gwRipple>3.5) gwRipple=0;
                if(collPhaseTime>8) nextPhase();
            }
            case 9 -> { // quiet aftermath — loop back after a long pause
                ellipseProgress=1.0;
                if(collPhaseTime>14){ collPhase=0; collPhaseTime=0; collTime=0;
                    starburstStars.clear(); bhSpiralR=0.4; ellipseProgress=0; gwRipple=0; }
            }
        }
    }
    private void nextPhase(){ collPhase++; collPhaseTime=0; }

    // ── COLLISION PAINT ─────────────────────────────────────────
    private void paintCollision(int cx,int cy,double sc){
        // collTime drives the orbital mechanics for phases 0-4
        double decay=Math.exp(-collTime*0.030), sep=2.4*decay, orb=collTime*0.85;

        // ── Phase-dependent galaxy positions ──
        double aX,aY,bX,bY;
        double eps2=0.05*0.05, tidalG=2.2;

        if(collPhase<=1){
            // Normal approach + first pass
            aX=sep*Math.cos(orb);   aY=sep*Math.sin(orb)*0.22;
            bX=-sep*Math.cos(orb);  bY=-sep*Math.sin(orb)*0.22;
        } else if(collPhase==2){
            // Separation — galaxies pull apart then slow
            double t=Math.min(1,collPhaseTime/6.0);
            double sepNow=0.3+0.8*t*(1-t)*4; // arc out then back
            aX= sepNow*0.7; aY= sepNow*0.15;
            bX=-sepNow*0.7; bY=-sepNow*0.15;
        } else if(collPhase==3){
            // Second pass — closer orbit
            double t=collPhaseTime/12.0;
            double sepNow=0.6*(1-t*0.7);
            aX= sepNow*Math.cos(orb*1.3); aY= sepNow*Math.sin(orb*1.3)*0.18;
            bX=-sepNow*Math.cos(orb*1.3); bY=-sepNow*Math.sin(orb*1.3)*0.18;
        } else {
            // Merged — both at center
            aX=0; aY=0; bX=0; bY=0;
        }

        double prox=Math.max(0,1-sep/2.4), amp=1+prox*5.5;

        // ── Dark matter halos ──
        if(showDarkMatter && collPhase<7){
            double haloR=collPhase>=4?sc*3.5*(1+ellipseProgress):sc*2.5;
            ren.darkMatterHalo((int)(cx+aX*sc),(int)(cy+aY*sc),(int)haloR);
            if(collPhase<4) ren.darkMatterHalo((int)(cx+bX*sc),(int)(cy+bY*sc),(int)(sc*2.0));
        }

        // ── Nebulae ──
        if(showNebulae && collPhase<6){
            for(NebulaCloud n:collA.nebulae){double nx=aX+n.r*Math.cos(n.angle),ny=aY+n.r*Math.sin(n.angle)*AND_COS;ren.nebula((int)(cx+nx*sc),(int)(cy+ny*sc),(int)(n.size*sc*.8),n.cr,n.cg,n.cb,n.alpha*.7f,n.dark);}
            if(collPhase<4){for(NebulaCloud n:collB.nebulae){double nx=bX+n.r*Math.cos(n.angle),ny=bY+n.r*Math.sin(n.angle)*SOM_COS;ren.nebula((int)(cx+nx*sc),(int)(cy+ny*sc),(int)(n.size*sc*.8),n.cr,n.cg,n.cb,n.alpha*.7f,n.dark);}}
        }

        // ── Phase 0-4: draw both galaxies with tidal forces ──
        if(collPhase<=4){
            for(Star s:collA.stars){
                double lx=s.r*Math.cos(s.angle),ly=s.r*Math.sin(s.angle)*AND_COS+s.diskZ*AND_SIN;
                double wx=aX+lx,wy=aY+ly,dx=bX-wx,dy=bY-wy,r2=dx*dx+dy*dy+eps2,rr=Math.sqrt(r2);
                double ownR=Math.sqrt(lx*lx+ly*ly*4+.001),tidal=collPhase<3?Math.min(1.9,(tidalG*ownR*amp)/r2):Math.min(2.5,(tidalG*ownR*amp*1.4)/r2);
                float a=s.alpha(); ren.dot((int)(cx+(wx+dx/rr*tidal)*sc),(int)(cy+(wy+dy/rr*tidal)*sc),s.cr*a,s.cg*a,s.cb*a,Math.min(4,s.glow));
            }
            for(Star s:collB.stars){
                double lx=s.r*Math.cos(s.angle),ly=s.r*Math.sin(s.angle)*SOM_COS+s.diskZ*SOM_SIN;
                double wx=bX+lx,wy=bY+ly,dx=aX-wx,dy=aY-wy,r2=dx*dx+dy*dy+eps2,rr=Math.sqrt(r2);
                double ownR=Math.sqrt(lx*lx+ly*ly*4+.001),tidal=collPhase<3?Math.min(1.9,(tidalG*ownR*amp)/r2):Math.min(2.5,(tidalG*ownR*amp*1.4)/r2);
                float a=s.alpha(); ren.dot((int)(cx+(wx+dx/rr*tidal)*sc),(int)(cy+(wy+dy/rr*tidal)*sc),s.cr*a,s.cg*a,s.cb*a,Math.min(4,s.glow));
            }
            ren.dustLane((int)(cx+bX*sc),(int)(cy+bY*sc),(int)(1.4*sc*.96),Math.max(3,(int)(1.4*sc*.048)));
            // BH dots
            ren.dot((int)(cx+aX*sc),(int)(cy+aY*sc),160,200,255,4); ren.dot((int)(cx+aX*sc),(int)(cy+aY*sc),255,255,255,3);
            ren.dot((int)(cx+bX*sc),(int)(cy+bY*sc),255,215,120,4); ren.dot((int)(cx+bX*sc),(int)(cy+bY*sc),255,255,255,3);
            // Core merge flash phase 4
            if(collPhase==4){ float f=(float)Math.min(1.0,collPhaseTime/3.0); for(int r=4;r>=0;r--) ren.dot(cx,cy,255*f,240*f*(1-r*.1f),170*f*(1-r*.15f),r); ren.dot(cx,cy,255*f,255*f,220*f,4); }
        }

        // ── Phase 5: STARBURST ──
        if(collPhase==5){
            // Fading remnants of both galaxies
            float fade=(float)Math.max(0,1-collPhaseTime/6.0);
            for(Star s:collA.stars){ double lx=s.r*Math.cos(s.angle)*0.6,ly=s.r*Math.sin(s.angle)*AND_COS*0.6; float a=s.alpha()*fade; if(a>0.05f) ren.dot((int)(cx+lx*sc),(int)(cy+ly*sc),s.cr*a,s.cg*a,s.cb*a,0); }
            for(Star s:collB.stars){ double lx=s.r*Math.cos(s.angle)*0.6,ly=s.r*Math.sin(s.angle)*SOM_COS*0.6; float a=s.alpha()*fade; if(a>0.05f) ren.dot((int)(cx+lx*sc),(int)(cy+ly*sc),s.cr*a,s.cg*a,s.cb*a,0); }
            // Hot starburst glow at centre
            ren.darkMatterHalo(cx,cy,(int)(sc*1.2));
            // New hot blue starburst stars flying outward
            for(float[] bs:starburstStars){
                float life=bs[4]/7f; int ki=life>0.6f?3:life>0.3f?2:1;
                ren.dot((int)(cx+bs[0]*sc*0.5),(int)(cy+bs[1]*sc*0.5),(int)(bs[5]*life),(int)(bs[6]*life),(int)(bs[7]*life),ki);
            }
            // Central starburst flash
            float sb=(float)(starburstAlpha*0.8+0.2*Math.sin(collPhaseTime*8));
            for(int r=4;r>=0;r--) ren.dot(cx,cy,(int)(80*sb),(int)(120*sb),(int)(255*sb),r);
        }

        // ── Phase 6: ELLIPTICAL FORMING ──
        if(collPhase==6){
            double ep=ellipseProgress;
            for(Star s:collA.stars){
                // Interpolate from spiral position toward elliptical random orbit
                double spiralX=s.r*Math.cos(s.angle), spiralY=s.r*Math.sin(s.angle)*AND_COS;
                double ellX=s.r*0.7*Math.cos(s.angle*0.5+s.diskZ), ellY=s.r*0.4*Math.sin(s.angle*0.5+s.diskZ);
                double px2=spiralX*(1-ep)+ellX*ep, py2=spiralY*(1-ep)+ellY*ep;
                float a=s.alpha()*0.7f;
                // Colour shifts to red/orange as stars age in elliptical
                float cr2=Math.min(255,s.cr*(float)(1+ep*0.3)), cg2=s.cg*(float)(1-ep*0.4f), cb2=s.cb*(float)(1-ep*0.7f);
                ren.dot((int)(cx+px2*sc),(int)(cy+py2*sc),cr2*a,Math.max(0,cg2*a),Math.max(0,cb2*a),0);
            }
            for(Star s:collB.stars){
                double spiralX=s.r*Math.cos(s.angle), spiralY=s.r*Math.sin(s.angle)*SOM_COS;
                double ellX=s.r*0.65*Math.cos(s.angle*0.5+s.diskZ+1.2), ellY=s.r*0.38*Math.sin(s.angle*0.5+s.diskZ+1.2);
                double px2=spiralX*(1-ep)+ellX*ep, py2=spiralY*(1-ep)+ellY*ep;
                float a=s.alpha()*0.7f;
                float cr2=Math.min(255,s.cr*(float)(1+ep*0.3)), cg2=s.cg*(float)(1-ep*0.4f), cb2=s.cb*(float)(1-ep*0.7f);
                ren.dot((int)(cx+px2*sc),(int)(cy+py2*sc),cr2*a,Math.max(0,cg2*a),Math.max(0,cb2*a),0);
            }
            ren.darkMatterHalo(cx,cy,(int)(sc*2.2));
        }

        // ── Phase 7: BLACK HOLE INSPIRAL ──
        if(collPhase==7){
            // Draw elliptical galaxy background
            for(Star s:collA.stars){ double ex=s.r*0.7*Math.cos(s.angle*0.5+s.diskZ),ey=s.r*0.4*Math.sin(s.angle*0.5+s.diskZ); float a=s.alpha()*0.5f; ren.dot((int)(cx+ex*sc),(int)(cy+ey*sc),(int)(s.cr*a*0.9f),(int)(Math.max(0,s.cg*a*0.6f)),(int)(Math.max(0,s.cb*a*0.3f)),0); }
            // Two BHs spiralling together
            int bhAx=(int)(cx+Math.cos(bhSpiralAngle)*bhSpiralR*sc);
            int bhAy=(int)(cy+Math.sin(bhSpiralAngle)*bhSpiralR*sc*0.3);
            int bhBx=(int)(cx-Math.cos(bhSpiralAngle)*bhSpiralR*sc);
            int bhBy=(int)(cy-Math.sin(bhSpiralAngle)*bhSpiralR*sc*0.3);
            // Accretion disk glow around each BH
            for(int r=4;r>=0;r--){ ren.dot(bhAx,bhAy,(int)(160*(1-r*.18f)),(int)(200*(1-r*.18f)),255,r); }
            for(int r=4;r>=0;r--){ ren.dot(bhBx,bhBy,255,(int)(215*(1-r*.18f)),(int)(80*(1-r*.18f)),r); }
            ren.darkMatterHalo(cx,cy,(int)(sc*1.8));
        }

        // ── Phase 8: GRAVITATIONAL WAVES ──
        if(collPhase==8){
            // Merged BH at centre
            for(int r=4;r>=0;r--) ren.dot(cx,cy,(int)(255*(1-r*.15f)),(int)(255*(1-r*.15f)),(int)(200*(1-r*.2f)),r);
            // Ripple rings drawn via Graphics2D (done in paintComponent overlay)
            // Draw settled elliptical
            for(Star s:collA.stars){ double ex=s.r*0.7*Math.cos(s.angle*0.5+s.diskZ),ey=s.r*0.4*Math.sin(s.angle*0.5+s.diskZ); float a=s.alpha()*0.45f; ren.dot((int)(cx+ex*sc),(int)(cy+ey*sc),(int)(s.cr*a*0.85f),(int)(Math.max(0,s.cg*a*0.5f)),(int)(Math.max(0,s.cb*a*0.2f)),0); }
        }

        // ── Phase 9: QUIET AFTERMATH ──
        if(collPhase==9){
            float quietness=(float)Math.min(1,collPhaseTime/6.0);
            for(Star s:collA.stars){ double ex=s.r*0.7*Math.cos(s.angle*0.5+s.diskZ),ey=s.r*0.4*Math.sin(s.angle*0.5+s.diskZ); float a=s.alpha()*(0.4f-quietness*0.1f); if(a>0) ren.dot((int)(cx+ex*sc),(int)(cy+ey*sc),(int)(s.cr*a*0.8f),(int)(Math.max(0,s.cg*a*0.45f)),(int)(Math.max(0,s.cb*a*0.15f)),0); }
            for(Star s:collB.stars){ double ex=s.r*0.65*Math.cos(s.angle*0.5+s.diskZ+1.2),ey=s.r*0.38*Math.sin(s.angle*0.5+s.diskZ+1.2); float a=s.alpha()*(0.35f-quietness*0.1f); if(a>0) ren.dot((int)(cx+ex*sc),(int)(cy+ey*sc),(int)(s.cr*a*0.8f),(int)(Math.max(0,s.cg*a*0.45f)),(int)(Math.max(0,s.cb*a*0.15f)),0); }
            // Single massive BH
            for(int r=4;r>=0;r--) ren.dot(cx,cy,(int)(220*(1-r*.15f)),(int)(180*(1-r*.2f)),(int)(80*(1-r*.25f)),r);
        }

        collAX=aX; collAY=aY; collBX=bX; collBY=bY;
    }

    // ── OVERLAY ──────────────────────────────────────────────────
    private void drawOverlay(Graphics2D g2){
        switch(mode){
            case 0 -> {
                double activeZoomV=lastScrolledLeft?zoomA:zoomB;
                double fadeOtherV=Math.max(0,Math.min(1,(2.2-activeZoomV)/1.0));
                // Show both labels at default, fade the non-focused one
                float alphaA=lastScrolledLeft?1f:(float)fadeOtherV;
                float alphaB=lastScrolledLeft?(float)fadeOtherV:1f;
                if(alphaA>0.05f){
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alphaA));
                    galLabel(g2,W/4,86,andGal,lblRA);
                }
                if(alphaB>0.05f){
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alphaB));
                    galLabel(g2,3*W/4,86,somGal,lblRS);
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1f));
                if(activeZoomV<=1.05){
                    // Default: show hint
                    g2.setFont(new Font("Courier New",Font.PLAIN,9));
                    g2.setColor(new Color(45,80,130));
                    String sep="◄── 25.46 Million light-years apart ──►";
                    FontMetrics fms=g2.getFontMetrics();
                    g2.drawString(sep,W/2-fms.stringWidth(sep)/2,CH/2+76+12);
                    g2.setFont(new Font("Courier New",Font.PLAIN,8));
                    g2.setColor(new Color(35,60,100));
                    g2.drawString("Scroll over a galaxy to zoom in  |  Double-click to reset",W/2-125,CH/2+76+24);
                } else {
                    // Zoomed: show which galaxy and zoom level
                    String lbl=lastScrolledLeft?String.format("Andromeda M31  —  %.1fx",zoomA):String.format("Sombrero M104  —  %.1fx",zoomB);
                    g2.setFont(new Font("Courier New",Font.BOLD,11));
                    g2.setColor(lastScrolledLeft?new Color(140,190,255):new Color(255,210,80));
                    FontMetrics fml=g2.getFontMetrics();
                    g2.drawString(lbl,W/2-fml.stringWidth(lbl)/2,86);
                    g2.setFont(new Font("Courier New",Font.PLAIN,8));
                    g2.setColor(new Color(45,80,130));
                    g2.drawString("Scroll out or double-click to reset",W/2-80,CH+76-4);
                }
            }
            case 1 -> { galLabel(g2,W/2,86,andGal,lblRA); zInfo(g2,zoom[1]); }
            case 2 -> { galLabel(g2,W/2,86,somGal,lblRS); zInfo(g2,zoom[2]); }
            case 3 -> { galLabel(g2,W/2,86,mwGal,lblRA);
                g2.setFont(new Font("Courier New",Font.BOLD,10)); g2.setColor(new Color(150,255,150,200));
                g2.drawString("☀  You are here — ~26,000 ly from centre  |  Our galaxy in 4.5 Byr will collide with Andromeda",W/2-320,CH+44);
                zInfo(g2,zoom[3]); }
            case 4 -> {
                String t="ANDROMEDA M31  ×  SOMBRERO M104  —  TIDAL MERGER";
                g2.setFont(new Font("Courier New",Font.BOLD,13));FontMetrics fm=g2.getFontMetrics();
                g2.setColor(new Color(255,168,50,218));g2.drawString(t,(W-fm.stringWidth(t))/2,72);
                String[]phaseLabels={"→ APPROACHING","↝ FIRST PASS","↔ SEPARATING","↝ SECOND PASS","⚡ CORE MERGER","⭐ STARBURST","◯ ELLIPTICAL FORMING","⚫ BH INSPIRAL","〰 GRAVITATIONAL WAVES","✦ QUIET AFTERMATH"};
                g2.setFont(new Font("Courier New",Font.PLAIN,10));g2.setColor(new Color(255,200,80));
                g2.drawString(phaseLabels[Math.min(collPhase,9)]+" | Stage "+(collPhase+1)+"/10",14,62);
                int cxA=(int)(W/2+collAX*120*zoom[4]+panX[4]),cxB=(int)(W/2+collBX*120*zoom[4]+panX[4]),labY=CH/2+76+(int)panY[4]-165;
                if(collPhase<5){ g2.setFont(new Font("Courier New",Font.BOLD,10)); g2.setColor(new Color(130,170,255,188));g2.drawString("ANDROMEDA M31",cxA-46,labY); g2.setColor(new Color(255,200,100,188));g2.drawString("SOMBRERO M104",cxB-46,labY); }
                else if(collPhase>=6){ g2.setFont(new Font("Courier New",Font.BOLD,11)); g2.setColor(new Color(255,200,150,200)); String lbl=collPhase>=9?"GIANT ELLIPTICAL GALAXY":"ELLIPTICAL FORMING..."; FontMetrics fm2=g2.getFontMetrics(); g2.drawString(lbl,(W-fm2.stringWidth(lbl))/2,CH/2+76); }
                zInfo(g2,zoom[4]);
            }
            case 5,6 -> {}
        }
        if(mode<5){
            if(wavelength>0){ g2.setFont(new Font("Courier New",Font.BOLD,11)); g2.setColor(WL_COLS[wavelength]); g2.drawString("⬛ "+WL_NAMES[wavelength]+" VIEW",W/2-60,CH+76-22); }
            if(showDarkMatter){g2.setColor(new Color(160,100,255,180));g2.setFont(new Font("Courier New",Font.BOLD,10));g2.drawString("◉ DARK MATTER HALO VISIBLE",14,CH+76-22);}
            if(showGravWell){g2.setColor(new Color(80,200,200,180));g2.setFont(new Font("Courier New",Font.BOLD,10));g2.drawString("GRAVITY WELL GRID ON",showDarkMatter?270:14,CH+76-22);}
            g2.setFont(new Font("Courier New",Font.PLAIN,9));g2.setColor(new Color(22,48,70));
            String hint="SCROLL=Zoom  DRAG=Pan  RIGHT-DRAG=3D  H=Help";
            FontMetrics fmh=g2.getFontMetrics();g2.drawString(hint,W-fmh.stringWidth(hint)-10,CH+76-4);
        }
    }

    private void galLabel(Graphics2D g2,int cx,int y,Galaxy gal,Rectangle hit){
        Color col=gal.labelCol;
        g2.setFont(new Font("Courier New",Font.BOLD,12));FontMetrics fm=g2.getFontMetrics();
        int nx=cx-fm.stringWidth(gal.name)/2;
        boolean hov=hoverX>=0&&hit.width>0&&hit.contains(hoverX,hoverY);
        if(hov){g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),20));g2.fillRoundRect(nx-8,y-14,fm.stringWidth(gal.name)+16,18,6,6);}
        g2.setColor(hov?col.brighter():col);g2.drawString(gal.name,nx,y);
        hit.setBounds(nx-8,y-14,fm.stringWidth(gal.name)+16,18);
        String sub=gal==andGal?"2.537 Mly  ·  220,000 ly  ·  77° incl.  ·  ~1 Trillion stars":"28 Mly  ·  50,000 ly  ·  84° incl.  ·  BH: ~1B M\u2609";
        g2.setFont(new Font("Courier New",Font.PLAIN,9));fm=g2.getFontMetrics();
        g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),105));
        g2.drawString(sub,cx-fm.stringWidth(sub)/2,y+13);
    }
    private void zInfo(Graphics2D g2,double z){
        g2.setFont(new Font("Courier New",Font.PLAIN,9));g2.setColor(new Color(24,50,72));
        g2.drawString(String.format("zoom:%.1fx | scroll=zoom drag=pan right-drag=3D rotate dbl-click=reset 3D",z),10,CH-4);
    }

    // ── TOOLBAR (between canvas and HUD) ─────────────────────────
    private void drawToolbar(Graphics2D g2){
        int ty=CH+76+2; int bh=20, gap=4;
        g2.setColor(new Color(2,6,18,200)); g2.fillRect(0,CH+76,W,26);
        g2.setColor(new Color(12,28,52)); g2.drawLine(0,CH+76,W,CH+76); g2.drawLine(0,CH+76+25,W,CH+76+25);

        // ── SPEED ──
        int bw=58; g2.setFont(new Font("Courier New",Font.BOLD,9)); g2.setColor(new Color(100,150,200)); g2.drawString("SPEED:",8,ty+14);
        for(int i=0;i<4;i++){
            int x=56+i*(bw+gap); SRECT[i]=new Rectangle(x,ty,bw,bh);
            boolean act=speedIdx==i, hov=SRECT[i].contains(hoverX,hoverY);
            Color[]sc2={new Color(60,160,255),new Color(60,210,80),new Color(255,190,30),new Color(255,80,40)};
            Color bc=sc2[i];
            g2.setColor(act?new Color(bc.getRed()/4,bc.getGreen()/4,bc.getBlue()/4):
                        hov?new Color(bc.getRed()/6,bc.getGreen()/6,bc.getBlue()/6):new Color(8,15,32));
            g2.fillRoundRect(x,ty,bw,bh,5,5);
            g2.setColor(act?bc:hov?new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),200):new Color(45,80,125));
            g2.setStroke(new BasicStroke(act?1.8f:hov?1.2f:.9f)); g2.drawRoundRect(x,ty,bw,bh,5,5); g2.setStroke(new BasicStroke(1f));
            g2.setFont(new Font("Courier New",act?Font.BOLD:Font.PLAIN,9));
            g2.setColor(act?new Color(Math.min(255,bc.getRed()+80),Math.min(255,bc.getGreen()+80),Math.min(255,bc.getBlue()+80)):
                        hov?bc:new Color(100,145,185));
            FontMetrics fm=g2.getFontMetrics(); g2.drawString(SPL[i],x+(bw-fm.stringWidth(SPL[i]))/2,ty+14);
        }

        // ── WAVELENGTH ──
        int wx=314; g2.setFont(new Font("Courier New",Font.BOLD,9)); g2.setColor(new Color(100,150,200)); g2.drawString("VIEW:",wx,ty+14);
        String[]wls={"VIS","IR","X-RAY","RADIO"}; int wbw=50;
        for(int i=0;i<4;i++){
            int x=wx+38+i*(wbw+gap); WLRECT[i]=new Rectangle(x,ty,wbw,bh);
            boolean act=wavelength==i, hov=WLRECT[i].contains(hoverX,hoverY);
            Color wc=WL_COLS[i];
            g2.setColor(act?new Color(wc.getRed()/4,wc.getGreen()/4,wc.getBlue()/4):
                        hov?new Color(wc.getRed()/6,wc.getGreen()/6,wc.getBlue()/6):new Color(8,15,32));
            g2.fillRoundRect(x,ty,wbw,bh,5,5);
            g2.setColor(act?wc:hov?new Color(wc.getRed(),wc.getGreen(),wc.getBlue(),200):new Color(45,80,125));
            g2.setStroke(new BasicStroke(act?1.8f:hov?1.2f:.9f)); g2.drawRoundRect(x,ty,wbw,bh,5,5); g2.setStroke(new BasicStroke(1f));
            g2.setFont(new Font("Courier New",act?Font.BOLD:Font.PLAIN,9));
            g2.setColor(act?new Color(Math.min(255,wc.getRed()+80),Math.min(255,wc.getGreen()+80),Math.min(255,wc.getBlue()+80)):
                        hov?wc:new Color(100,145,185));
            FontMetrics fm=g2.getFontMetrics(); g2.drawString(wls[i],x+(wbw-fm.stringWidth(wls[i]))/2,ty+14);
        }

        // ── TOGGLE BUTTONS ──
        int tx2=548;
        tbNebula .setBounds(tx2,      ty,80,bh); tglBtn(g2,tbNebula, "NEBULAE",   showNebulae,   new Color(150,100,255));
        tbDarkMat.setBounds(tx2+84,   ty,92,bh); tglBtn(g2,tbDarkMat,"DARK MATTER",showDarkMatter,new Color(180,80,255));
        tbGravWel.setBounds(tx2+180,  ty,84,bh); tglBtn(g2,tbGravWel,"GRAV WELL", showGravWell,  new Color(80,200,200));
        tbPlace  .setBounds(tx2+268,  ty,72,bh); tglBtn(g2,tbPlace,  "PLACE \u2605",placingStars,new Color(255,200,80));

        // ── UTILITY BUTTONS ──
        int ux=W-256;
        tbShot.setBounds(ux,    ty,76,bh); utilBtn2(g2,tbShot, "\u25a3 SHOT", new Color(50,160,80));
        tbGif .setBounds(ux+80, ty,80,bh); utilBtn2(g2,tbGif,  recording?"■ STOP":"● GIF", recording?new Color(255,60,60):new Color(200,80,80));
        tbFly .setBounds(ux+164,ty,80,bh); utilBtn2(g2,tbFly,  "F WARP",   flythroughMode?new Color(100,200,255):new Color(50,100,150));
    }

    private void tglBtn(Graphics2D g2, Rectangle r, String lbl, boolean on, Color col){
        boolean hov=r.contains(hoverX,hoverY);
        // Brighter fill when on, subtle when off
        g2.setColor(on?new Color(col.getRed()/4,col.getGreen()/4,col.getBlue()/4):
                    hov?new Color(col.getRed()/6,col.getGreen()/6,col.getBlue()/6):new Color(8,16,38));
        g2.fillRoundRect(r.x,r.y,r.width,r.height,5,5);
        // Border bright when on
        g2.setColor(on?col:hov?new Color(col.getRed(),col.getGreen(),col.getBlue(),180):new Color(35,65,105));
        g2.setStroke(new BasicStroke(on?1.8f:hov?1.2f:0.9f));
        g2.drawRoundRect(r.x,r.y,r.width,r.height,5,5); g2.setStroke(new BasicStroke(1f));
        // Label text — always legible
        g2.setFont(new Font("Courier New",on?Font.BOLD:Font.PLAIN,8));
        g2.setColor(on?new Color(Math.min(255,col.getRed()+80),Math.min(255,col.getGreen()+80),Math.min(255,col.getBlue()+80)):
                    hov?col:new Color(100,145,185));
        FontMetrics fm=g2.getFontMetrics(); g2.drawString(lbl,r.x+(r.width-fm.stringWidth(lbl))/2,r.y+r.height-5);
    }

    private void utilBtn2(Graphics2D g2, Rectangle r, String lbl, Color col){
        boolean hov=r.contains(hoverX,hoverY);
        g2.setColor(hov?new Color(col.getRed()/4,col.getGreen()/4,col.getBlue()/4):new Color(8,16,38));
        g2.fillRoundRect(r.x,r.y,r.width,r.height,5,5);
        g2.setColor(hov?col:new Color(col.getRed(),col.getGreen(),col.getBlue(),200));
        g2.setStroke(new BasicStroke(hov?1.6f:1.2f)); g2.drawRoundRect(r.x,r.y,r.width,r.height,5,5); g2.setStroke(new BasicStroke(1f));
        g2.setFont(new Font("Courier New",Font.BOLD,9));
        g2.setColor(hov?new Color(Math.min(255,col.getRed()+80),Math.min(255,col.getGreen()+80),Math.min(255,col.getBlue()+80)):
                    new Color(Math.min(255,col.getRed()+40),Math.min(255,col.getGreen()+40),Math.min(255,col.getBlue()+40)));
        FontMetrics fm=g2.getFontMetrics(); g2.drawString(lbl,r.x+(r.width-fm.stringWidth(lbl))/2,r.y+r.height-5);
    }

    // ── HUD ──────────────────────────────────────────────────────
    private static final String[][] A_ST={
        {"TYPE","SA(s)b Barred Spiral"},{"DIAMETER","220,000 ly"},{"DISTANCE","2.537 Million ly"},{"STARS","~1 Trillion"},
        {"MASS","1.5 × 10¹² M\u2609"},{"DARK MATTER","~1.2 × 10¹² M\u2609"},{"SPIRAL ARMS","2 major + spurs"},{"INCLINATION","77° from face-on"},
        {"ANG. SIZE","3.167° (6× Moon)"},{"MAGNITUDE","3.44 (naked eye)"},{"APPROACH","110 km/s blueshift"},{"AGE","~10 Billion years"},
        {"BH MASS","~140 Million M\u2609"},{"DISK THICK","~1,000 ly"},{"HII REGIONS","Hundreds visible"},{"FATE","Approaching Milky Way"},
    };
    private static final String[][] S_ST={
        {"TYPE","SA(s)a Unbarred Spiral"},{"DIAMETER","50,000 ly"},{"DISTANCE","28 Million ly"},{"STARS","~100 Billion"},
        {"MASS","8 × 10¹¹ M\u2609"},{"BLACK HOLE","~1 Billion M\u2609"},{"DARK MATTER","~7 × 10¹¹ M\u2609"},{"INCLINATION","84° edge-on"},
        {"ANG. SIZE","8.7′ × 3.5′"},{"MAGNITUDE","8.98 (binoculars)"},{"REDSHIFT","+0.003416"},{"AGE","~13 Billion years"},
        {"DISK THICK","~600 ly"},{"GLOB. CLUSTERS","~2,000 (rich!)"},{"DUST LANE","Prominent/Defining"},{"BH INFLUENCE","~1,800 ly radius"},
    };

    private static final String[][] MW_ST={
        {"TYPE","SBbc Barred Spiral"},{"DIAMETER","~100,000 ly"},{"DISTANCE","We are inside it!"},{"STARS","200-400 Billion"},
        {"MASS","~1.5 × 10¹² M\u2609"},{"DARK MATTER","~80% of total mass"},{"SPIRAL ARMS","4 major arms"},{"OUR LOCATION","Orion Arm, 26kly out"},
        {"ANG. SIZE","Full sky band"},{"MAGNITUDE","Visible naked eye"},{"ROTATION","225M yr per orbit"},{"AGE","~13.6 Billion years"},
        {"BH MASS","Sgr A*: 4M M\u2609"},{"DISK THICK","~1,000 ly"},{"SATELLITES","LMC, SMC, 50+ dwarfs"},{"FATE","Merge w/ Andromeda ~4.5Byr"},
    };

    private void drawHUD(Graphics2D g2){
        int py=CH+76+32;
        g2.setPaint(new GradientPaint(0,CH+76,new Color(2,6,20,248),0,H,new Color(1,3,12,255)));
        g2.fillRect(0,CH+76,W,H-CH-76); g2.setPaint(null);
        g2.setColor(new Color(10,28,55)); g2.drawLine(0,CH,W,CH);
        switch(mode){
            case 0 -> { sCol(g2,22,py,"ANDROMEDA M31",new Color(170,212,255),A_ST); compBars(g2,W/2-120,py); sCol(g2,W/2+104,py,"SOMBRERO M104",new Color(255,200,100),S_ST); }
            case 1 -> sRow(g2,py,W,"ANDROMEDA GALAXY — M31 / NGC 224",new Color(170,212,255),A_ST);
            case 2 -> sRow(g2,py,W,"SOMBRERO GALAXY — M104 / NGC 4594",new Color(255,200,100),S_ST);
            case 3 -> sRow(g2,py,W,"MILKY WAY — OUR HOME GALAXY",new Color(180,255,180),MW_ST);
            case 4 -> collHUD(g2,py);
            case 5,6 -> {} // HR Diagram and Scale have own full rendering
        }
        g2.setFont(new Font("Courier New",Font.PLAIN,9));g2.setColor(new Color(12,36,56));
        g2.drawString("ANALYTIC ORBITS · TIDAL STREAMS · EMISSION/DARK NEBULAE · GRAVITATIONAL LENSING · BLOOM · STAR LIFECYCLE · HR DIAGRAM · SCALE COMPARE",14,H-8);
    }

    private void sCol(Graphics2D g2,int x,int y,String t,Color col,String[][]st){
        g2.setFont(new Font("Courier New",Font.BOLD,10));g2.setColor(col);g2.drawString(t,x,y);
        g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),35));g2.drawLine(x,y+3,x+275,y+3);
        g2.setFont(new Font("Courier New",Font.PLAIN,9));
        for(int i=0;i<st.length;i++){int rx=x+(i%2)*135,ry=y+15+(i/2)*17;g2.setColor(new Color(36,64,88));g2.drawString(st[i][0]+":",rx,ry);g2.setColor(new Color(152,188,210));g2.drawString(st[i][1],rx,ry+10);}
    }

    private void sRow(Graphics2D g2,int y,int w,String t,Color col,String[][]st){
        g2.setFont(new Font("Courier New",Font.BOLD,10));g2.setColor(col);g2.drawString(t,22,y);
        g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),33));g2.drawLine(22,y+3,w-22,y+3);
        g2.setFont(new Font("Courier New",Font.PLAIN,9));int cw=(w-44)/4;
        for(int i=0;i<st.length;i++){int rx=22+(i%4)*cw,ry=y+15+(i/4)*17;g2.setColor(new Color(36,64,88));g2.drawString(st[i][0]+":",rx,ry);g2.setColor(new Color(152,188,210));g2.drawString(st[i][1],rx,ry+10);}
    }

    private void collHUD(Graphics2D g2,int py){
        // Phase names and descriptions
        String[]phaseNames={"APPROACHING","FIRST PASS — TIDAL STREAMS","SEPARATION","SECOND PASS","FINAL CORE MERGER","⭐ STARBURST","ELLIPTICAL GALAXY FORMING","⚫ BLACK HOLE INSPIRAL","〰 GRAVITATIONAL WAVES","QUIET AFTERMATH"};
        String[]phaseDesc={
            "Gravity draws the galaxies together across millions of light years",
            "Galaxies pass through each other — tidal forces fling stars into streams",
            "Galaxies separate but gravity will pull them back for another pass",
            "Second approach — stronger tidal distortion, more streams ejected",
            "Cores spiral together — two supermassive black holes approach",
            "Gas clouds collide triggering massive burst of new hot blue star formation",
            "Spiral arms destroyed — stars settle into random elliptical orbits",
            "Two black holes orbit each other, losing energy to gravitational radiation",
            "Black hole merger detected as gravitational waves rippling through spacetime",
            "Giant elliptical galaxy — old, red, quiet. Star formation essentially over."
        };
        g2.setFont(new Font("Courier New",Font.BOLD,11));g2.setColor(new Color(255,138,38));
        g2.drawString("ANDROMEDA M31 × SOMBRERO M104 — TIDAL MERGER",28,py);
        // Phase progress bar
        int pbx=28,pby=py+8,pbw=W-56,pbh=6;
        g2.setColor(new Color(15,30,55)); g2.fillRoundRect(pbx,pby,pbw,pbh,4,4);
        float prog=(collPhase*10+Math.min(10,(float)(collPhaseTime/getPhaseDuration(collPhase))*10))/100f;
        g2.setPaint(new GradientPaint(pbx,pby,new Color(80,120,255),pbx+(int)(pbw*prog),pby,new Color(255,140,40)));
        g2.fillRoundRect(pbx,pby,(int)(pbw*prog),pbh,4,4); g2.setPaint(null);
        // Phase label
        g2.setFont(new Font("Courier New",Font.BOLD,10));
        g2.setColor(new Color(255,200,80));
        String phaseLbl="STAGE "+(collPhase+1)+"/10: "+phaseNames[Math.min(collPhase,9)];
        g2.drawString(phaseLbl,28,py+24);
        g2.setFont(new Font("Courier New",Font.PLAIN,9));g2.setColor(new Color(100,145,185));
        g2.drawString(phaseDesc[Math.min(collPhase,9)],28,py+36);
        g2.setColor(new Color(28,54,82));g2.drawLine(28,py+42,W-28,py+42);
        // Data columns
        int col=W/3;
        g2.setFont(new Font("Courier New",Font.BOLD,10));
        g2.setColor(new Color(128,172,255));g2.drawString("◆ ANDROMEDA M31",28,py+55);
        g2.setColor(new Color(255,172,52));g2.drawString("◆ MERGER PHYSICS",col+10,py+55);
        g2.setColor(new Color(255,198,98));g2.drawString("◆ SOMBRERO M104",col*2+10,py+55);
        String[][]af={{"Type","SA(s)b Barred"},{"Diameter","220,000 ly"},{"Stars","~1 Trillion"},{"Mass","1.5×10¹²M\u2609"},{"BH","~140M M\u2609"},{"Incl.","77°"},{"Distance","2.537 Mly"},{"Mag.","3.44"}};
        String[][]mf={{"Star collisions","Essentially ZERO"},{"Why?","99.9999% empty"},{"Gas clouds","DO collide"},{"Starbursts","New stars born"},{"Tidal streams","Form in 1st pass"},{"Dark matter","Halos merge first"},{"Driver","Dynamical friction"},{"Result","Giant elliptical"},{"Timescale","Billions of years"},{"GW source","BH-BH merger"}};
        String[][]sf={{"Type","SA(s)a Unbarred"},{"Diameter","50,000 ly"},{"Stars","~100 Billion"},{"Mass","8×10¹¹M\u2609"},{"BH","~1B M\u2609"},{"Incl.","84° edge-on"},{"Distance","28 Mly"},{"Clusters","~2,000"}};
        g2.setFont(new Font("Courier New",Font.PLAIN,9));int lh=16;
        for(int i=0;i<af.length&&py+68+i*lh<H-15;i++){int ry=py+68+i*lh;g2.setColor(new Color(50,85,122));g2.drawString(af[i][0]+":",28,ry);g2.setColor(new Color(155,195,230));g2.drawString(af[i][1],115,ry);}
        for(int i=0;i<mf.length&&py+68+i*lh<H-15;i++){int ry=py+68+i*lh;g2.setColor(new Color(128,90,32));g2.drawString(mf[i][0]+":",col+10,ry);g2.setColor(new Color(212,178,125));g2.drawString(mf[i][1],col+125,ry);}
        for(int i=0;i<sf.length&&py+68+i*lh<H-15;i++){int ry=py+68+i*lh;g2.setColor(new Color(125,85,30));g2.drawString(sf[i][0]+":",col*2+10,ry);g2.setColor(new Color(250,195,95));g2.drawString(sf[i][1],col*2+115,ry);}
    }
    private double getPhaseDuration(int phase){
        double[]d={18,14,12,12,10,10,10,10,8,14}; return phase<d.length?d[phase]:10;
    }

    private void compBars(Graphics2D g2,int x,int y){
        g2.setFont(new Font("Courier New",Font.BOLD,9));g2.setColor(new Color(25,50,76));g2.drawString("COMPARISON",x,y-8);
        String[][]d={{"Diameter","220k ly","100","50k ly","23"},{"Stars","1 Trillion","100","100B","10"},{"BH Mass","140M M\u2609","14","1B M\u2609","100"},{"Distance","2.5 Mly","9","28 Mly","100"}};
        Color ac=new Color(77,184,255),sc2=new Color(255,170,51);
        for(int i=0;i<d.length;i++){int by=y+i*34;g2.setFont(new Font("Courier New",Font.PLAIN,9));g2.setColor(new Color(34,60,84));g2.drawString(d[i][0].toUpperCase(),x,by);mbar(g2,x,by+4,96,Integer.parseInt(d[i][2]),ac,d[i][1]);mbar(g2,x+100,by+4,96,Integer.parseInt(d[i][4]),sc2,d[i][3]);}
        g2.setFont(new Font("Courier New",Font.PLAIN,8));g2.setColor(ac);g2.drawString("■ Andromeda",x,y+140);g2.setColor(sc2);g2.drawString("■ Sombrero",x+80,y+140);
    }

    private void mbar(Graphics2D g2,int x,int y,int mW,int pct,Color col,String lbl){
        int fw=Math.max(1,mW*pct/100);g2.setColor(new Color(5,12,28));g2.fillRoundRect(x,y,mW,5,4,4);
        g2.setPaint(new GradientPaint(x,y,new Color(col.getRed(),col.getGreen(),col.getBlue(),80),x+fw,y,col));
        g2.fillRoundRect(x,y,fw,5,4,4);g2.setPaint(null);
        g2.setFont(new Font("Courier New",Font.PLAIN,8));g2.setColor(col);g2.drawString(lbl,x,y+13);
    }

    // ── LIVE STATS ───────────────────────────────────────────────
    private void drawLiveStats(Graphics2D g2){
        int px2=W-165,py2=84,pw=155,ph=98;
        g2.setColor(new Color(2,8,22,210));g2.fillRoundRect(px2,py2,pw,ph,8,8);
        g2.setColor(new Color(20,45,75));g2.drawRoundRect(px2,py2,pw,ph,8,8);
        g2.setFont(new Font("Courier New",Font.BOLD,9));g2.setColor(new Color(55,100,148));g2.drawString("LIVE STATS",px2+8,py2+13);
        g2.setFont(new Font("Courier New",Font.PLAIN,9));
        int totalStars=andGal.stars.size()+somGal.stars.size();
        String[]lines={"FPS: "+fps,
            "Stars: "+totalStars,
            "Supernovae: "+supernovae.size(),
            "Zoom: "+(mode==0?String.format("%.1f/%.1f",zoomA,zoomB)+"x":String.format("%.1fx",zoom[mode])),
            "Speed: "+SPL[speedIdx],
            "Mode: "+BTNS[mode].replace("⚡ ","")};
        Color[]cols2={new Color(100,220,100),new Color(180,210,255),new Color(255,180,80),new Color(140,190,230),new Color(220,170,80),new Color(150,180,220)};
        for(int i=0;i<lines.length;i++){g2.setColor(cols2[i]);g2.drawString(lines[i],px2+8,py2+26+i*13);}
    }

    // ── HELP OVERLAY ─────────────────────────────────────────────
    private void drawHelp(Graphics2D g2){
        g2.setColor(new Color(0,5,18,220)); g2.fillRoundRect(W/2-280,CH/2-200,560,400,16,16);
        g2.setColor(new Color(40,80,130)); g2.drawRoundRect(W/2-280,CH/2-200,560,400,16,16);
        g2.setFont(new Font("Courier New",Font.BOLD,13)); g2.setColor(new Color(140,200,255));
        g2.drawString("KEYBOARD SHORTCUTS   (H to close)",W/2-200,CH/2-175);
        g2.setColor(new Color(30,60,95)); g2.drawLine(W/2-265,CH/2-162,W/2+265,CH/2-162);
        String[][]keys={{"SPACE","Pause/Resume"},{"F","Warp Flythrough"},{"D","Dark Matter Halo"},{"G","Gravity Well Grid"},{"N","Nebulae Toggle"},{"P","Place Stars Mode"},
                         {"S","Screenshot"},{"R","Record GIF (3 sec)"},{"H","Help (this screen)"},{"V","Visible light"},{"I","Infrared view"},{"X","X-Ray view"},
                         {"Q","Radio wave view"},{"1-7","Switch mode"},{"+ / -","Zoom"},{"Arrows","Pan"},{"Right-drag","3D Rotate"},{"Dbl-click","Reset view"}};
        g2.setFont(new Font("Courier New",Font.PLAIN,10));
        int cols=2,rows=(int)Math.ceil(keys.length/(double)cols);
        for(int i=0;i<keys.length;i++){
            int col=i/rows,row=i%rows;
            int rx=W/2-260+col*270,ry=CH/2-148+row*18;
            g2.setColor(new Color(80,160,220)); g2.drawString("["+keys[i][0]+"]",rx,ry);
            g2.setColor(new Color(155,190,220)); g2.drawString(keys[i][1],rx+55,ry);
        }
        g2.setFont(new Font("Courier New",Font.PLAIN,9));g2.setColor(new Color(40,75,110));
        g2.drawString("Right-click drag = 3D rotate galaxy  |  Scroll = zoom  |  Drag = pan",W/2-215,CH/2+175);
    }

    private void drawPlacingHint(Graphics2D g2){
        g2.setFont(new Font("Courier New",Font.BOLD,11));
        g2.setColor(new Color(255,210,60,200));
        g2.drawString("★ STAR PLACEMENT MODE — Click anywhere in the galaxy to add a star  [P to exit]",W/2-340,CH/2-30);
        if(hoverX>0&&hoverX<W&&hoverY>0&&hoverY<CH){
            g2.setColor(new Color(255,220,80,160));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(hoverX-6,hoverY-6,12,12);
            g2.drawLine(hoverX-10,hoverY,hoverX+10,hoverY);
            g2.drawLine(hoverX,hoverY-10,hoverX,hoverY+10);
            g2.setStroke(new BasicStroke(1f));
        }
    }

    private void drawRecordingBar(Graphics2D g2){
        float prog=(float)(recTimer/REC_DUR);
        int bx=W/2-150,by=CH-18,bw=300,bh=8;
        g2.setColor(new Color(180,30,30,200)); g2.fillRoundRect(W/2-75,CH/2+15,150,22,8,8);
        g2.setColor(new Color(255,100,100)); g2.setFont(new Font("Courier New",Font.BOLD,10));
        g2.drawString(String.format("● REC %.1f / %.1f sec",recTimer,REC_DUR),W/2-68,CH/2+30);
        g2.setColor(new Color(8,16,32)); g2.fillRoundRect(bx,by,bw,bh,6,6);
        g2.setPaint(new GradientPaint(bx,by,new Color(220,60,60),bx+(int)(bw*prog),by,new Color(255,120,60)));
        g2.fillRoundRect(bx,by,(int)(bw*prog),bh,6,6); g2.setPaint(null);
    }

    private void drawMessages(Graphics2D g2){
        long now=System.currentTimeMillis();
        if(shotMsg!=null){ float a=shotMsg.startsWith("✓")?Math.max(0,(3500-(now-shotTime))/1000f):1f; if(a<=0){shotMsg=null;} else drawMsg(g2,shotMsg,a,new Color(40,180,70)); }
        if(recMsg!=null){ float a=recMsg.startsWith("✓")?Math.max(0,(4000-(now-recMsgTime))/1000f):1f; if(a<=0){recMsg=null;} else drawMsg(g2,recMsg,a,recMsg.startsWith("✓")?new Color(40,180,70):new Color(220,60,60)); }
    }

    private void drawMsg(Graphics2D g2,String msg,float alpha,Color col){
        g2.setFont(new Font("Courier New",Font.BOLD,10));FontMetrics fm=g2.getFontMetrics();
        int tw=fm.stringWidth(msg)+20,tx=(W-tw)/2,ty=CH-50;
        g2.setColor(new Color(0,18,8,(int)(200*alpha)));g2.fillRoundRect(tx,ty,tw,22,8,8);
        g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),(int)(180*alpha)));g2.drawRoundRect(tx,ty,tw,22,8,8);
        g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),(int)(255*alpha)));g2.drawString(msg,tx+10,ty+15);
    }

    private void drawTooltip(Graphics2D g2){
        if(tipText==null||tipText.isEmpty()) return;
        String[] lines=tipText.split("\n");
        g2.setFont(new Font("Courier New",Font.PLAIN,10));FontMetrics fm=g2.getFontMetrics();
        int lh=fm.getHeight(),mw=0; for(String l:lines) mw=Math.max(mw,fm.stringWidth(l));
        int tw=mw+16,th=lines.length*lh+10,tx=Math.min(tipX,W-tw-5),ty=Math.max(5,Math.min(tipY,CH-th-5));
        g2.setColor(new Color(5,12,30,222));g2.fillRoundRect(tx,ty,tw,th,8,8);
        g2.setColor(new Color(45,90,140));g2.drawRoundRect(tx,ty,tw,th,8,8);
        for(int i=0;i<lines.length;i++){g2.setColor(i==0?new Color(175,210,255):new Color(125,162,195));g2.drawString(lines[i],tx+8,ty+fm.getAscent()+5+i*lh);}
    }

    // ── BUTTONS — two rows of buttons at top ─────────────────────
    private void drawButtons(Graphics2D g2){
        // Row 1: modes 0-3 (4 buttons), Row 2: modes 4-6 (3 buttons)
        int bw=148, bh=30, gap=7;
        int row1Count=4, row2Count=3;
        int row1Tot=row1Count*bw+(row1Count-1)*gap;
        int row2Tot=row2Count*bw+(row2Count-1)*gap;
        int row1X=(W-row1Tot)/2, row2X=(W-row2Tot)/2;
        int row1Y=4, row2Y=38;
        int totalH=76;
        // Full-width top bar background
        g2.setPaint(new GradientPaint(0,0,new Color(3,8,25,255),0,totalH,new Color(6,14,38,250)));
        g2.fillRect(0,0,W,totalH); g2.setPaint(null);
        g2.setColor(new Color(30,65,120,180)); g2.drawLine(0,totalH-1,W,totalH-1);
        // Subtle divider between rows
        g2.setColor(new Color(20,45,85,100)); g2.drawLine(0,36,W,36);

        Color[]bordCols={new Color(80,200,255),new Color(140,175,255),new Color(255,210,80),new Color(180,255,180),
                         new Color(255,120,40),new Color(100,220,180),new Color(200,140,255)};
        Color[]fillCols={new Color(0,55,105),new Color(18,38,95),new Color(75,52,0),new Color(0,65,20),
                         new Color(95,22,0),new Color(0,65,55),new Color(55,20,80)};

        for(int i=0;i<BTNS.length;i++){
            int row=i<4?0:1;
            int col=i<4?i:i-4;
            int sx2=i<4?row1X:row2X;
            int y=i<4?row1Y:row2Y;
            int x=sx2+col*(bw+gap);
            BRECT[i]=new Rectangle(x,y,bw,bh);
            boolean act=mode==i, hov=BRECT[i].contains(hoverX,hoverY);
            Color bc=bordCols[i];
            // Fill
            if(act) g2.setPaint(new GradientPaint(x,y,fillCols[i],x,y+bh,new Color(fillCols[i].getRed()/2,fillCols[i].getGreen()/2,fillCols[i].getBlue()/2)));
            else     g2.setPaint(new GradientPaint(x,y,hov?new Color(15,28,60):new Color(8,14,35),x,y+bh,new Color(4,8,20)));
            g2.fillRoundRect(x,y,bw,bh,7,7); g2.setPaint(null);
            if(act){ g2.setColor(new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),45)); g2.fillRoundRect(x-2,y-2,bw+4,bh+4,9,9); }
            g2.setColor(act?bc:hov?new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),180):new Color(35,65,115));
            g2.setStroke(new BasicStroke(act?2.0f:hov?1.4f:0.9f));
            g2.drawRoundRect(x,y,bw,bh,7,7); g2.setStroke(new BasicStroke(1f));
            // Key hint
            g2.setFont(new Font("Courier New",Font.BOLD,7));
            g2.setColor(act?new Color(bc.getRed(),bc.getGreen(),bc.getBlue(),180):new Color(50,85,135));
            g2.drawString("["+(i+1)+"]",x+4,y+9);
            // Label
            g2.setFont(new Font("Courier New",act?Font.BOLD:Font.PLAIN,10));
            g2.setColor(act?new Color(Math.min(255,bc.getRed()+60),Math.min(255,bc.getGreen()+60),Math.min(255,bc.getBlue()+60)):hov?bc:new Color(110,155,210));
            FontMetrics fm=g2.getFontMetrics();
            g2.drawString(BTNS[i],x+(bw-fm.stringWidth(BTNS[i]))/2,y+21);
        }
    }

    // ── HR DIAGRAM ───────────────────────────────────────────────
    private void paintHRDiagram(Graphics2D g2){
        g2.setColor(Color.BLACK); g2.fillRect(0,0,W,H);
        // Margins: left=65 (Y axis), right=220 (legend), top=76+40, bottom=30 (X axis)
        int mx=65, my=76+40, mw=W-295, mh=CH-55;

        // Background gradient — hot left = deep blue, cool right = deep red
        g2.setPaint(new GradientPaint(mx,my,new Color(4,6,28),mx+mw,my+mh,new Color(20,5,12)));
        g2.fillRect(mx,my,mw,mh); g2.setPaint(null);
        g2.setColor(new Color(22,48,85)); g2.drawRect(mx,my,mw,mh);

        // Title
        g2.setFont(new Font("Courier New",Font.BOLD,13));
        g2.setColor(new Color(200,220,255));
        g2.drawString("HERTZSPRUNG-RUSSELL DIAGRAM",mx,my-22);
        g2.setFont(new Font("Courier New",Font.PLAIN,9));
        g2.setColor(new Color(55,95,140));
        g2.drawString("Luminosity vs Temperature — each dot is a star from the simulation",mx,my-8);

        // ── LOG SCALE setup ──
        // X: temperature 3000K–50000K, log scale, HOT=LEFT
        // Y: luminosity 10^-4 to 10^6 solar, log scale, BRIGHT=TOP
        double logTmin=Math.log10(3000), logTmax=Math.log10(50000); // 3.477 to 4.699
        double logLmin=-4.0, logLmax=6.0;

        // Grid lines + X axis ticks
        int[]tickTemps={50000,30000,20000,10000,7500,6000,5000,4000,3500,3000};
        g2.setFont(new Font("Courier New",Font.PLAIN,7));
        for(int t:tickTemps){
            double tNorm=(Math.log10(t)-logTmin)/(logTmax-logTmin);
            int lx=mx+(int)(mw*(1-tNorm)); // reversed — hot left
            g2.setColor(new Color(18,38,65)); g2.drawLine(lx,my,lx,my+mh);
            g2.setColor(new Color(65,100,145)); g2.drawString(t>=1000?t/1000+"K":t+"",lx-8,my+mh+10);
        }
        // X axis label
        g2.setFont(new Font("Courier New",Font.BOLD,9));
        g2.setColor(new Color(100,145,190));
        g2.drawString("◄ HOT   TEMPERATURE   COOL ►",mx+mw/2-90,my+mh+22);

        // Y axis ticks + grid lines
        int[]lumPows={6,5,4,3,2,1,0,-1,-2,-3,-4};
        String[]lumLabels={"10⁶","10⁵","10⁴","10³","10²","10","1","0.1","0.01","10⁻³","10⁻⁴"};
        g2.setFont(new Font("Courier New",Font.PLAIN,7));
        for(int k=0;k<lumPows.length;k++){
            double lNorm=(lumPows[k]-logLmin)/(logLmax-logLmin);
            int ly=my+(int)(mh*(1-lNorm));
            if(ly<my||ly>my+mh) continue;
            g2.setColor(new Color(18,38,65)); g2.drawLine(mx,ly,mx+mw,ly);
            g2.setColor(new Color(65,100,145)); g2.drawString(lumLabels[k],mx-38,ly+4);
        }
        // Y axis label (rotated)
        g2.setFont(new Font("Courier New",Font.BOLD,9));
        g2.setColor(new Color(100,145,190));
        java.awt.geom.AffineTransform old=g2.getTransform();
        g2.rotate(-Math.PI/2, mx-50, my+mh/2);
        g2.drawString("LUMINOSITY (Solar = 1)",mx-50-60,my+mh/2+4);
        g2.setTransform(old);

        // ── PLOT STARS ──
        // Each star gets assigned temp+lum from its colour (spectral type)
        Galaxy[]gals={andGal,somGal,mwGal};
        Random hrRng=new Random(42); // fixed seed so plot is stable
        for(Galaxy gal:gals){
            for(int i=0;i<gal.stars.size();i+=2){
                Star s=gal.stars.get(i);
                float bb=s.cb/255f, rr=s.cr/255f, gg=s.cg/255f;
                double temp,logLum;
                // Classify by colour into spectral type, assign realistic temp+lum on main sequence
                if(bb>0.85f&&rr<0.35f){       // O-type: hot blue
                    temp=35000+hrRng.nextGaussian()*8000;
                    logLum=4.5+hrRng.nextGaussian()*0.4;
                } else if(bb>0.7f&&rr<0.65f){ // B-type
                    temp=16000+hrRng.nextGaussian()*4000;
                    logLum=3.2+hrRng.nextGaussian()*0.5;
                } else if(bb>0.5f){            // A-type: white
                    temp=9000+hrRng.nextGaussian()*1000;
                    logLum=1.5+hrRng.nextGaussian()*0.4;
                } else if(rr>0.9f&&gg>0.88f){ // G-type: yellow (Sun-like)
                    temp=5800+hrRng.nextGaussian()*300;
                    logLum=0.0+hrRng.nextGaussian()*0.3;
                } else if(rr>0.9f&&gg>0.65f){ // K-type: orange
                    temp=4500+hrRng.nextGaussian()*300;
                    logLum=-0.6+hrRng.nextGaussian()*0.3;
                } else {                       // M-type: red dwarf
                    temp=3300+hrRng.nextGaussian()*200;
                    logLum=-1.8+hrRng.nextGaussian()*0.4;
                }
                // Giants/supergiants shoot up in luminosity — only glow>=3 (true supergiants)
                // glow==1 are just bright core stars, NOT giants on HR diagram
                if(s.glow>=3){ logLum+=hrRng.nextGaussian()<0?4.5:3.8; temp+=hrRng.nextGaussian()*2000; }
                // No boost for glow==1 — they stay on main sequence

                // Map to screen using log scale
                double tNorm=(Math.log10(Math.max(3000,temp))-logTmin)/(logTmax-logTmin);
                double lNorm=(logLum-logLmin)/(logLmax-logLmin);
                int px2=mx+(int)(mw*(1-tNorm));
                int py2=my+(int)(mh*(1-lNorm));
                if(px2<mx||px2>mx+mw||py2<my||py2>my+mh) continue;

                int alpha=s.glow>=3?220:s.glow>=1?140:60;
                int sz=s.glow>=3?3:s.glow>=1?2:1;
                g2.setColor(new Color(Math.min(255,(int)s.cr),Math.min(255,(int)s.cg),Math.min(255,(int)s.cb),alpha));
                g2.fillOval(px2-sz,py2-sz,sz*2,sz*2);
            }
        }

        // ── SEQUENCE LABELS ──
        g2.setFont(new Font("Courier New",Font.BOLD,9));
        // Main sequence diagonal label
        g2.setColor(new Color(80,140,200,160));
        g2.drawString("MAIN SEQUENCE",mx+mw/2-20,my+mh/2+30);
        g2.setColor(new Color(255,150,50,170));
        g2.drawString("RED GIANTS",mx+mw*2/3,my+mh/4);
        g2.setColor(new Color(120,210,255,170));
        g2.drawString("SUPERGIANTS",mx+mw/8,my+25);
        g2.setColor(new Color(200,210,255,140));
        g2.drawString("WHITE DWARFS",mx+mw*3/4,my+mh-18);

        // Sun marker
        double sunTNorm=(Math.log10(5778)-logTmin)/(logTmax-logTmin);
        double sunLNorm=(0.0-logLmin)/(logLmax-logLmin);
        int sunX=mx+(int)(mw*(1-sunTNorm));
        int sunY=my+(int)(mh*(1-sunLNorm));
        g2.setColor(new Color(255,240,80)); g2.setStroke(new BasicStroke(1.8f));
        g2.drawOval(sunX-5,sunY-5,10,10); g2.setStroke(new BasicStroke(1f));
        g2.setFont(new Font("Courier New",Font.BOLD,8));
        g2.setColor(new Color(255,240,80));
        g2.drawString("☀ Sun",sunX+8,sunY+4);

        // ── LEGEND (right side) ──
        int lx=mx+mw+12, ly=my;
        g2.setColor(new Color(3,8,22,220)); g2.fillRoundRect(lx-4,ly-4,210,mh+8,8,8);
        g2.setColor(new Color(20,48,85)); g2.drawRoundRect(lx-4,ly-4,210,mh+8,8,8);
        g2.setFont(new Font("Courier New",Font.BOLD,9)); g2.setColor(new Color(120,165,210));
        g2.drawString("SPECTRAL TYPES",lx+4,ly+12);
        g2.setColor(new Color(20,48,85)); g2.drawLine(lx,ly+16,lx+200,ly+16);

        String[][]leg={
            {"O","70,130,255","Hottest  >30,000K","100,000× Sun"},
            {"B","150,185,255","Hot  10–30,000K","100–10,000× Sun"},
            {"A","215,225,255","White  7.5–10,000K","2–100× Sun"},
            {"G","255,240,160","Yellow  5–6,000K","0.6–1.5× Sun ☀"},
            {"K","255,185,80","Orange  3.5–5,000K","0.1–0.6× Sun"},
            {"M","255,100,60","Red  <3,500K","<0.1× Sun"},
        };
        for(int k=0;k<leg.length;k++){
            String[]rgb=leg[k][1].split(",");
            Color sc2=new Color(Integer.parseInt(rgb[0].trim()),Integer.parseInt(rgb[1].trim()),Integer.parseInt(rgb[2].trim()));
            int ey=ly+28+k*26;
            g2.setColor(sc2); g2.fillOval(lx+4,ey,10,10);
            g2.setFont(new Font("Courier New",Font.BOLD,9)); g2.setColor(sc2);
            g2.drawString(leg[k][0]+"-type",lx+18,ey+9);
            g2.setFont(new Font("Courier New",Font.PLAIN,7)); g2.setColor(new Color(90,130,170));
            g2.drawString(leg[k][2],lx+18,ey+18);
            g2.drawString(leg[k][3],lx+18,ey+26);
        }
        // Extra notes
        int ny=ly+28+leg.length*26+8;
        g2.setColor(new Color(20,48,85)); g2.drawLine(lx,ny,lx+200,ny);
        g2.setFont(new Font("Courier New",Font.BOLD,8)); g2.setColor(new Color(100,200,255));
        g2.drawString("★ Large dot = Giant/Supergiant",lx+4,ny+12);
        g2.setFont(new Font("Courier New",Font.PLAIN,7)); g2.setColor(new Color(70,110,155));
        g2.drawString("Stars from Andromeda, Sombrero",lx+4,ny+24);
        g2.drawString("and Milky Way all plotted here",lx+4,ny+34);
        g2.drawString("Total: ~"+((andGal.stars.size()+somGal.stars.size()+mwGal.stars.size())/2)+" data points",lx+4,ny+46);

        // HUD panel below
        g2.setPaint(new GradientPaint(0,CH+76,new Color(2,6,20,255),0,H,new Color(1,3,12,255)));
        g2.fillRect(0,CH+76,W,H-CH-76); g2.setPaint(null);
        g2.setFont(new Font("Courier New",Font.PLAIN,8)); g2.setColor(new Color(40,75,115));
        g2.drawString("HR DIAGRAM — The main sequence runs diagonally top-left to bottom-right. Giants branch upper-right. White dwarfs sit lower-left. The Sun is a G-type main sequence star.",14,CH+76+14);
        g2.setColor(new Color(25,55,85));
        g2.drawString("Stars spend ~90% of their life on the main sequence. When hydrogen runs out, they expand into giants, then end as white dwarfs, neutron stars, or black holes depending on mass.",14,CH+76+26);
    }

    // ── SCALE COMPARE ────────────────────────────────────────────
    private void paintScaleCompare(Graphics2D g2){
        g2.setColor(Color.BLACK); g2.fillRect(0,0,W,H);

        // Title
        g2.setFont(new Font("Courier New",Font.BOLD,14));
        g2.setColor(new Color(200,220,255));
        g2.drawString("GALAXY SCALE COMPARISON",W/2-145,76+22);
        g2.setFont(new Font("Courier New",Font.PLAIN,9));
        g2.setColor(new Color(60,100,145));
        g2.drawString("Each galaxy rendered to scale  |  Andromeda is 2.2x wider than our Milky Way",W/2-205,76+36);

        // We render 3 real galaxies side by side at relative scale
        // Andromeda = 220kly (largest shown), MW = 100kly, Sombrero = 50kly
        // Max visual radius = 150px for Andromeda
        double maxKly=220.0, maxPx=145.0;
        double[]diams={220,100,50}; // kly
        double[]xFracs={0.20,0.52,0.80};
        String[]names={"Andromeda M31","Milky Way","Sombrero M104"};
        String[]subtitles={"220,000 ly  ·  ~1 Trillion stars  ·  2.537 Mly away",
                           "100,000 ly  ·  ~300 Billion stars  ·  You are here ☀",
                           "50,000 ly   ·  ~100 Billion stars  ·  28 Mly away"};
        Color[]labelCols={new Color(140,190,255),new Color(150,255,150),new Color(255,210,80)};
        Galaxy[]gals={andGal,mwGal,somGal};
        double[]cosIs={AND_COS,0.22,SOM_COS};
        double[]sinIs={AND_SIN,0.975,SOM_SIN};
        boolean[]dusts={false,false,true};

        int canvasTop=76+45, canvasH=CH-45;
        int cy2=canvasTop+canvasH/2;

        // Render all 3 galaxies into ONE shared canvas image (additive)
        Renderer scRen=new Renderer(W,canvasH);
        for(int i=0;i<3;i++){
            int cx2=(int)(xFracs[i]*W);
            double sc=diams[i]/maxKly*maxPx;
            int galCY=canvasH/2;

            if(showDarkMatter) scRen.darkMatterHalo(cx2,galCY,(int)(sc*2.8));
            if(showNebulae){
                for(NebulaCloud n:gals[i].nebulae){
                    double nx=n.r*Math.cos(n.angle), ny=n.r*Math.sin(n.angle)*cosIs[i];
                    int nr2=Math.min(60,(int)(n.size*sc*0.85));
                    scRen.nebula((int)(cx2+nx*sc),(int)(galCY+ny*sc),nr2,n.cr,n.cg,n.cb,n.alpha,n.dark);
                }
            }
            for(Star s:gals[i].stars){
                double lx=s.r*Math.cos(s.angle);
                double ly2=s.r*Math.sin(s.angle)*cosIs[i]+s.diskZ*sinIs[i];
                int sx2=(int)(cx2+lx*sc);
                int sy2=(int)(galCY+ly2*sc);
                float a=s.alpha();
                scRen.dot(sx2,sy2,s.cr*a,s.cg*a,s.cb*a,Math.min(4,s.glow));
            }
            if(dusts[i]){
                int hw=(int)(1.4*sc*0.96), hh=Math.max(2,(int)(1.4*sc*0.048));
                scRen.dustLane(cx2,galCY,hw,hh);
            }
        }
        // Bloom once for all 3 galaxies together
        scRen.bloom(0.4f,3,2,22f);
        g2.drawImage(scRen.image(),0,canvasTop,null);

        // Draw rings and labels for each galaxy
        for(int i=0;i<3;i++){
            int cx2=(int)(xFracs[i]*W);
            int ringR=(int)(diams[i]/maxKly*maxPx);
            g2.setColor(new Color(labelCols[i].getRed(),labelCols[i].getGreen(),labelCols[i].getBlue(),40));
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(cx2-ringR,cy2-ringR,ringR*2,ringR*2);
            g2.setStroke(new BasicStroke(1f));
            int labelY=Math.min(cy2+ringR+18,CH+76-12);
            g2.setFont(new Font("Courier New",Font.BOLD,11));
            g2.setColor(labelCols[i]);
            FontMetrics fm=g2.getFontMetrics();
            g2.drawString(names[i],cx2-fm.stringWidth(names[i])/2,labelY);
            g2.setFont(new Font("Courier New",Font.PLAIN,8));
            g2.setColor(new Color(labelCols[i].getRed(),labelCols[i].getGreen(),labelCols[i].getBlue(),180));
            if(labelY+12<CH+76)
                g2.drawString(subtitles[i],cx2-g2.getFontMetrics().stringWidth(subtitles[i])/2,labelY+12);
        }

        // "You are here" dot on Milky Way — screen coordinates
        int mwCx=(int)(xFracs[1]*W);
        double mwSc=diams[1]/maxKly*maxPx;
        int youX=mwCx+(int)(mwSc*0.52), youY=cy2-5;
        g2.setColor(new Color(255,255,100));
        g2.fillOval(youX-3,youY-3,6,6);
        g2.drawLine(youX,youY,youX+22,youY-16);
        g2.setFont(new Font("Courier New",Font.BOLD,9));
        g2.setColor(new Color(255,255,100));
        g2.drawString("☀ You are here",youX+24,youY-14);

        // Divider
        g2.setColor(new Color(15,35,65));
        g2.drawLine(0,CH+76,W,CH+76);

        // Stats table in HUD panel
        g2.setPaint(new GradientPaint(0,CH+76,new Color(2,6,20,255),0,H,new Color(1,3,12,255)));
        g2.fillRect(0,CH+76,W,H-CH-76); g2.setPaint(null);
        int ty2=CH+76+14;
        g2.setFont(new Font("Courier New",Font.BOLD,10));
        g2.setColor(new Color(150,190,230));
        g2.drawString("COMPARATIVE STATISTICS",22,ty2);
        g2.setColor(new Color(20,45,80)); g2.drawLine(22,ty2+4,W-22,ty2+4);
        String[][]table={
            {"Property","Andromeda M31","Milky Way","Sombrero M104"},
            {"Diameter","220,000 ly","100,000 ly","50,000 ly"},
            {"Stars","~1 Trillion","200-400 Billion","~100 Billion"},
            {"Distance","2.537 Million ly","We live here!","28 Million ly"},
            {"Black Hole","~140 Million M\u2609","Sgr A*: 4M M\u2609","~1 Billion M\u2609"},
            {"Type","Barred Spiral","Barred Spiral","Edge-on Spiral"},
            {"Spiral Arms","2 major + spurs","4 major arms","Ring-like (edge-on)"},
            {"Fate","Merge w/ Milky Way","Merge w/ Andromeda","Distant observer"},
        };
        int cw=(W-44)/4;
        g2.setFont(new Font("Courier New",Font.PLAIN,9));
        for(int row=0;row<table.length;row++){
            for(int col=0;col<4;col++){
                int rx=22+col*cw, ry=ty2+18+row*15;
                if(ry>H-8) continue;
                Color tc=row==0?new Color(100,150,200):col==0?new Color(60,100,145):
                         col==1?new Color(140,185,255):col==2?new Color(140,235,140):new Color(255,205,80);
                g2.setColor(tc); g2.drawString(table[row][col],rx,ry);
            }
        }
    }

    // ── STAR TYPE LEGEND ─────────────────────────────────────────
    private void drawStarLegend(Graphics2D g2){
        int lx=10, ly=CH+76-72, lh=12; // inside canvas bottom-left
        g2.setColor(new Color(3,8,22,200)); g2.fillRoundRect(lx-4,ly-10,165,70,6,6);
        g2.setColor(new Color(20,45,80)); g2.drawRoundRect(lx-4,ly-10,165,70,6,6);
        g2.setFont(new Font("Courier New",Font.BOLD,8)); g2.setColor(new Color(80,120,170));
        g2.drawString("STAR TYPES",lx,ly);
        String[][]leg={{"● O-type","70,130,255","Hottest, blue"},{"● B-type","140,180,255","Hot, blue-white"},
                       {"● A-type","215,225,255","White"},{"● G-type","255,240,180","Yellow (like Sun)"},
                       {"● K-type","255,185,90","Orange"},{"★ Supergiant","80,200,255","Massive, rare"}};
        g2.setFont(new Font("Courier New",Font.PLAIN,7));
        for(int i=0;i<leg.length;i++){
            String[]rgb=leg[i][1].split(",");
            g2.setColor(new Color(Integer.parseInt(rgb[0].trim()),Integer.parseInt(rgb[1].trim()),Integer.parseInt(rgb[2].trim())));
            FontMetrics fm=g2.getFontMetrics(); g2.drawString(leg[i][0],lx,ly+10+i*lh);
            g2.setColor(new Color(55,88,120)); g2.drawString(leg[i][2],lx+70,ly+10+i*lh);
        }
    }
    private BufferedImage buildBg(Random rng){
        BufferedImage b=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        int[] bpx=((DataBufferInt)b.getRaster().getDataBuffer()).getData();
        // Deep black base with slight blue tint
        for(int i=0;i<bpx.length;i++) bpx[i]=0x00000A;
        // Large faint nebula clouds — visible coloured patches like real APOD
        int[][]nebCols={{25,12,55},{12,30,60},{45,12,30},{12,45,30},{38,20,12},{20,8,48}};
        double[][]nebPos={{0.22,0.35},{0.75,0.22},{0.50,0.68},{0.15,0.72},{0.82,0.58},{0.42,0.15}};
        int[]nebRad={300,240,280,210,260,220};
        for(int n=0;n<nebCols.length;n++){
            int nx=(int)(nebPos[n][0]*W), ny=(int)(nebPos[n][1]*H), nr=nebRad[n];
            int x0=Math.max(0,nx-nr), x1=Math.min(W-1,nx+nr);
            int y0=Math.max(0,ny-nr), y1=Math.min(H-1,ny+nr);
            for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++){
                float dx=(x-nx)/(float)nr, dy=(y-ny)/(float)nr;
                float d2=dx*dx+dy*dy; if(d2>1f) continue;
                // Use layered noise-like variation
                float f=(float)(Math.exp(-d2*2.8)*1.0);
                float f2=(float)(Math.sin(dx*4+dy*3+n)*0.15+0.15); // subtle texture
                float ft=f*(0.85f+f2);
                int idx=y*W+x, c=bpx[idx];
                int r=Math.min(255,((c>>16)&0xFF)+(int)(nebCols[n][0]*ft));
                int g=Math.min(255,((c>>8)&0xFF)+(int)(nebCols[n][1]*ft));
                int bl=Math.min(255,(c&0xFF)+(int)(nebCols[n][2]*ft));
                bpx[idx]=(r<<16)|(g<<8)|bl;
            }
        }
        // Milky Way-like faint band across middle
        for(int y=0;y<H;y++) for(int x=0;x<W;x++){
            float dx=(x/(float)W-0.5f)*3f;
            float dy=(y/(float)H-0.42f)*8f;
            float band=(float)(Math.exp(-(dx*dx*0.4+dy*dy))*18);
            int idx=y*W+x, c=bpx[idx];
            int r=Math.min(255,((c>>16)&0xFF)+(int)(band*1.0));
            int g=Math.min(255,((c>>8)&0xFF)+(int)(band*0.8));
            int bl=Math.min(255,(c&0xFF)+(int)(band*0.5));
            bpx[idx]=(r<<16)|(g<<8)|bl;
        }
        // 200 tiny distant galaxy smudges
        for(int i=0;i<200;i++){
            int gx=rng.nextInt(W), gy=rng.nextInt(H);
            int gr=1+rng.nextInt(4);
            float gcr=rng.nextFloat()*0.5f+0.15f;
            int gcol=rng.nextInt(3);
            for(int dy=-gr;dy<=gr;dy++) for(int dx=-gr;dx<=gr;dx++){
                int px2=gx+dx, py2=gy+dy;
                if(px2<0||px2>=W||py2<0||py2>=H) continue;
                float d2=dx*dx+dy*dy*3; if(d2>gr*gr) continue;
                float f=(float)(Math.exp(-d2/(gr*gr)*3.5)*gcr*0.7);
                int idx=py2*W+px2, c=bpx[idx];
                int rv=Math.min(255,((c>>16)&0xFF)+(int)((gcol==0?22:15)*f));
                int gv=Math.min(255,((c>>8)&0xFF)+(int)((gcol==1?20:14)*f));
                int bv=Math.min(255,(c&0xFF)+(int)((gcol==2?28:18)*f));
                bpx[idx]=(rv<<16)|(gv<<8)|bv;
            }
        }
        return b;
    }

    private void buildBGStars(Random rng){
        bgStars.clear();
        // 10000 background stars with realistic colour distribution
        for(int i=0;i<10000;i++){
            BGStar s=new BGStar();
            s.x=rng.nextFloat()*W; s.y=rng.nextFloat()*CH;
            s.vx=(rng.nextFloat()-.5f)*.15f; s.vy=(rng.nextFloat()-.5f)*.05f;
            s.br=0.08f+rng.nextFloat()*.92f;
            s.twPhase=rng.nextFloat()*6.28f; s.twSpeed=0.15f+rng.nextFloat()*1.4f;
            float cv=rng.nextFloat();
            // Realistic star colour distribution: 70% white/blue-white, 15% orange/red, 15% blue
            if(cv<0.08f){s.rc=100;s.gc=130;s.bc=255;}
            else if(cv<0.18f){s.rc=160;s.gc=190;s.bc=255;}
            else if(cv<0.22f){s.rc=255;s.gc=180;s.bc=80;}
            else if(cv<0.26f){s.rc=255;s.gc=120;s.bc=60;}
            else if(cv<0.30f){s.rc=255;s.gc=220;s.bc=140;}
            else{int v=200+rng.nextInt(50);s.rc=Math.min(255,v);s.gc=Math.min(255,v+rng.nextInt(8));s.bc=Math.min(255,v+rng.nextInt(12));}
            s.size=s.br>.82f?1:0; bgStars.add(s);
        }
    }
}
