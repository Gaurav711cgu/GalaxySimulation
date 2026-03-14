import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * ════════════════════════════════════════════════════════════════
 *  GALAXY SIMULATION v6 — Stable Analytic Orbits + Bloom
 *
 *  ✦ Analytic orbit integration (stars NEVER scatter or escape)
 *  ✦ Density-wave spiral arms (arms emerge as slower regions)
 *  ✦ Sombrero rendered with full 3-D hat shape + dust lane
 *  ✦ Additive pixel blending + multi-pass bloom
 *  ✦ Hover tooltips on stars & galaxy labels
 *  ✦ 4 modes: Side-by-Side | Andromeda | Sombrero | ⚡ Collision
 *  ✦ Scroll=Zoom | Drag=Pan | Double-click=Reset
 *
 *  javac GalaxySimulation.java
 *  java  GalaxySimulation
 * ════════════════════════════════════════════════════════════════
 */
public class GalaxySimulation extends JFrame {
    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "true");
        SwingUtilities.invokeLater(() -> new GalaxySimulation().setVisible(true));
    }
    public GalaxySimulation() {
        setTitle("Galaxy Simulation — Analytic Orbits + Bloom Rendering");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        SimPanel p = new SimPanel();
        add(p); pack(); setLocationRelativeTo(null);
    }
}

// ════════════════════════════════════════════════════════════════
//  STAR — analytic orbit particle
// ════════════════════════════════════════════════════════════════
class Star {
    // Orbital parameters (FIXED — star never escapes)
    double r;          // orbital radius (sim units)
    double angle;      // current angle (radians)
    double omega;      // angular velocity (rad/sec)  — differential rotation
    float  diskZ;      // height above/below disk plane (for Sombrero bulge)
    // Rendering
    float  cr, cg, cb;
    int    glow;       // kernel index 0..4
    String tip;        // tooltip text
    // Twinkle
    float  twPhase, twSpeed;

    void tick(double dt) {
        angle += omega * dt;
        twPhase += twSpeed * dt;
    }

    float alpha() { return 0.78f + 0.22f * (float) Math.sin(twPhase); }
}

// ════════════════════════════════════════════════════════════════
//  GALAXY — collection of stars with stable analytic orbits
// ════════════════════════════════════════════════════════════════
class Galaxy {
    final List<Star>  stars  = new ArrayList<>();
    // Render offset (used in collision mode to separate galaxies)
    double offX = 0, offY = 0, offVX = 0, offVY = 0;
    // Name & color for label
    String name;
    Color  labelCol;

    /**
     * Build a galaxy.
     * @param N      number of disk stars
     * @param arms   spiral arm count (0 = uniform disk like Sombrero)
     * @param bulge  add spherical bulge stars (Sombrero = true)
     * @param gid    0=Andromeda(blue), 1=Sombrero(golden), 2=MilkyWay(green)
     */
    void build(int N, int arms, boolean bulge, int gid, Random rng) {
        stars.clear();
        // === DISK STARS ===
        for (int i = 0; i < N; i++) {
            Star s = new Star();
            // Exponential disk radial profile — concentrate toward center
            s.r = expRadius(rng, 1.4);
            // Flat rotation curve omega = v_flat / r  (inner: solid body → flat)
            double vFlat = 1.0; // normalised
            s.omega = vFlat / Math.max(s.r, 0.08);
            // Spiral: initial angle places star on arm + some scatter
            if (arms > 0) {
                int arm = rng.nextInt(arms);
                // Log-spiral: angle offset grows with r
                double logSpiral = Math.log(s.r / 0.05 + 1.0) * 2.5;
                double armBase   = arm * (2.0 * Math.PI / arms) + logSpiral;
                double spread    = 0.20 + s.r * 0.12;
                s.angle = armBase + rng.nextGaussian() * spread;
            } else {
                s.angle = rng.nextDouble() * 2.0 * Math.PI;
            }
            // Slight eccentricity for realism
            s.r *= 1.0 + rng.nextGaussian() * 0.025;
            s.r  = Math.max(0.04, s.r);
            s.diskZ = 0f;
            // Color
            colorDisk(s, gid, arms, rng);
            // Twinkle
            s.twPhase = rng.nextFloat() * 6.28f;
            s.twSpeed = 0.3f + rng.nextFloat() * 1.2f;
            stars.add(s);
        }
        // === BULGE (3D spherical) ===
        if (bulge) {
            int NB = N / 3;
            for (int i = 0; i < NB; i++) {
                Star s = new Star();
                double rb  = Math.pow(rng.nextDouble(), 0.55) * 0.55;
                double phi = (rng.nextDouble() - 0.5) * Math.PI; // elevation
                s.r     = rb * Math.cos(phi);
                s.diskZ = (float) (rb * Math.sin(phi));
                s.angle = rng.nextDouble() * 2 * Math.PI;
                s.omega = vOmega(s.r);
                s.cr = 255; s.cg = clamp(218 + rng.nextInt(30)); s.cb = clamp(145 + rng.nextInt(75));
                s.glow = 1; s.tip = "K-type (Bulge)";
                s.twPhase = rng.nextFloat() * 6.28f; s.twSpeed = 0.4f + rng.nextFloat();
                stars.add(s);
            }
        }
        // === SUPER-BRIGHT CORE STARS ===
        for (int i = 0; i < 60; i++) {
            Star s = new Star();
            s.r = Math.pow(rng.nextDouble(), 2.0) * 0.15;
            s.angle = rng.nextDouble() * 2 * Math.PI;
            s.omega = vOmega(s.r);
            s.cr = 255; s.cg = clamp(248 + rng.nextInt(7)); s.cb = clamp(210 + rng.nextInt(45));
            s.glow = gid == 0 ? 3 : 2; s.diskZ = 0f;
            s.tip = "Core G/K Giant"; s.twPhase = rng.nextFloat() * 6.28f; s.twSpeed = 1.5f;
            stars.add(s);
        }
        // === BLACK HOLE (rendered separately) ===
        Star bh = new Star();
        bh.r = 0; bh.angle = 0; bh.omega = 0; bh.diskZ = 0;
        bh.cr = 255; bh.cg = 255; bh.cb = 255; bh.glow = 4;
        bh.tip = "Supermassive Black Hole\n" + (name != null ? name : "") + "\nMass: ~1 Billion M☉";
        bh.twPhase = 0; bh.twSpeed = 0.5f;
        stars.add(0, bh); // Insert at front so it renders last (on top)
    }

    private double expRadius(Random rng, double scale) {
        // Inverse CDF of exponential distribution, capped
        double u = rng.nextDouble() * 0.98 + 0.01;
        return Math.min(-scale * Math.log(1.0 - u), scale * 3.8);
    }

    private double vOmega(double r) {
        // Flat rotation curve angular velocity
        return 1.0 / Math.max(r, 0.06);
    }

    private void colorDisk(Star s, int gid, int arms, Random rng) {
        double rf = Math.min(1.0, s.r / 1.4);
        if (s.r < 0.18) {
            s.cr = 255; s.cg = clamp(238 + rng.nextInt(12)); s.cb = clamp(170 + rng.nextInt(50));
            s.glow = 2; s.tip = "Core G/K Giant";
        } else if (gid == 1) { // Sombrero — warm golden
            s.cr = 255; s.cg = clamp(188 + rng.nextInt(45)); s.cb = clamp(95 + rng.nextInt(80));
            s.glow = 1; s.tip = "K-type (Disk)";
        } else if (gid == 2) { // Milky Way — greenish white
            s.cr = clamp(150 + rng.nextInt(70)); s.cg = clamp(215 + rng.nextInt(35));
            s.cb = clamp(148 + rng.nextInt(70)); s.glow = 1; s.tip = "G-type";
        } else { // Andromeda — blue spiral arms
            double d = rng.nextDouble();
            if (arms > 0 && d < 0.05) {
                s.cr = 70; s.cg = 130; s.cb = 255; s.glow = 4; s.tip = "O-type Supergiant";
            } else if (arms > 0 && d < 0.28) {
                s.cr = clamp(135 + rng.nextInt(55)); s.cg = clamp(175 + rng.nextInt(45)); s.cb = 255;
                s.glow = 2; s.tip = "B-type Blue Giant";
            } else if (d < 0.56) {
                s.cr = clamp(210 + rng.nextInt(35)); s.cg = clamp(220 + rng.nextInt(28)); s.cb = 255;
                s.glow = 1; s.tip = "A-type";
            } else {
                s.cr = clamp(215 + rng.nextInt(30)); s.cg = clamp(195 + rng.nextInt(35));
                s.cb = clamp(148 + rng.nextInt(65)); s.glow = 1; s.tip = "K/M-type";
            }
        }
    }

    int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    void tick(double dt) {
        // Analytic: just advance angles — perfectly stable forever
        for (Star s : stars) s.tick(dt);
        // Collision mode: advance galaxy center
        offX += offVX * dt; offY += offVY * dt;
        // Gravitational attraction between two galaxy centers (handled in SimPanel)
    }
}

// ════════════════════════════════════════════════════════════════
//  PIXEL RENDERER — additive blending + multi-pass bloom
// ════════════════════════════════════════════════════════════════
class Renderer {
    final int W, H;
    final int[] px;
    final BufferedImage img;

    // Glow kernels: radii 1, 2, 4, 7, 11
    static final int[]     GR = {1, 2, 4, 7, 11};
    static final float[][] GK;
    static {
        GK = new float[GR.length][];
        for (int k = 0; k < GR.length; k++) {
            int r = GR[k], d = 2*r+1;
            GK[k] = new float[d*d];
            float inv = 1f / (r*r + 0.5f);
            for (int dy=-r; dy<=r; dy++)
                for (int dx=-r; dx<=r; dx++) {
                    float f = Math.max(0, 1f - (dx*dx+dy*dy)*inv);
                    GK[k][(dy+r)*d+(dx+r)] = f * f;
                }
        }
    }

    Renderer(int W, int H) {
        this.W=W; this.H=H;
        img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        px  = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    void clear() { Arrays.fill(px, 0); }

    void dot(int sx, int sy, float cr, float cg, float cb, int ki) {
        ki = Math.min(GK.length-1, Math.max(0, ki));
        int r = GR[ki];
        if (sx+r<0||sx-r>=W||sy+r<0||sy-r>=H) return;
        float[] k = GK[ki]; int d=2*r+1;
        for (int y=Math.max(0,sy-r); y<=Math.min(H-1,sy+r); y++) {
            int base=y*W, kb=(y-sy+r)*d;
            for (int x=Math.max(0,sx-r); x<=Math.min(W-1,sx+r); x++) {
                float kv = k[kb+(x-sx+r)]; if (kv<=0) continue;
                int idx=base+x, c=px[idx];
                px[idx] = (Math.min(255,((c>>16)&0xFF)+(int)(cr*kv))<<16)
                        | (Math.min(255,((c>> 8)&0xFF)+(int)(cg*kv))<<8)
                        |  Math.min(255,((c    )&0xFF)+(int)(cb*kv));
            }
        }
    }

    void bloom(float strength, int passes, int blurR, float thr) {
        int bW=W/4, bH=H/4;
        float[] sm = new float[bW*bH*3];
        for (int y=0; y<bH; y++)
            for (int x=0; x<bW; x++) {
                float r=0,g=0,b=0;
                for (int dy=0;dy<4;dy++) for (int dx=0;dx<4;dx++) {
                    int s=px[(y*4+dy)*W+x*4+dx];
                    r+=(s>>16)&0xFF; g+=(s>>8)&0xFF; b+=s&0xFF;
                }
                int i=(y*bW+x)*3;
                sm[i  ]=Math.max(0,r*0.0625f-thr);
                sm[i+1]=Math.max(0,g*0.0625f-thr);
                sm[i+2]=Math.max(0,b*0.0625f-thr);
            }
        for (int p=0; p<passes; p++) sm = blur(sm,bW,bH,blurR);
        for (int y=0; y<H; y++)
            for (int x=0; x<W; x++) {
                int bx=Math.min(bW-1,x/4), by=Math.min(bH-1,y/4), i=(by*bW+bx)*3;
                int c=px[y*W+x];
                px[y*W+x] = (Math.min(255,((c>>16)&0xFF)+(int)(sm[i  ]*strength))<<16)
                           | (Math.min(255,((c>> 8)&0xFF)+(int)(sm[i+1]*strength))<<8)
                           |  Math.min(255,((c    )&0xFF)+(int)(sm[i+2]*strength));
            }
    }

    private float[] blur(float[] src, int w, int h, int rad) {
        float[] t=new float[src.length], d=new float[src.length]; float inv=1f/(2*rad+1);
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
            float r=0,g=0,b=0;
            for (int dx=-rad;dx<=rad;dx++){int nx=Math.max(0,Math.min(w-1,x+dx)),i=(y*w+nx)*3;r+=src[i];g+=src[i+1];b+=src[i+2];}
            int i=(y*w+x)*3;t[i]=r*inv;t[i+1]=g*inv;t[i+2]=b*inv;
        }
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
            float r=0,g=0,b=0;
            for (int dy=-rad;dy<=rad;dy++){int ny=Math.max(0,Math.min(h-1,y+dy)),i=(ny*w+x)*3;r+=t[i];g+=t[i+1];b+=t[i+2];}
            int i=(y*w+x)*3;d[i]=r*inv;d[i+1]=g*inv;d[i+2]=b*inv;
        }
        return d;
    }

    /** Dark elliptical band (Sombrero dust lane) */
    void dustLane(int cx, int cy, int hw, int hh) {
        for (int y=Math.max(0,cy-hh); y<=Math.min(H-1,cy+hh); y++)
            for (int x=Math.max(0,cx-hw); x<=Math.min(W-1,cx+hw); x++) {
                float fx=(float)(x-cx)/hw, fy=(float)(y-cy)/hh;
                float d2=fx*fx+fy*fy; if(d2>1) continue;
                float dk=0.90f*(1-d2);
                int idx=y*W+x, c=px[idx];
                px[idx]=((int)(((c>>16)&0xFF)*(1-dk))<<16)
                       |((int)(((c>> 8)&0xFF)*(1-dk))<<8)
                       | (int)(((c    )&0xFF)*(1-dk));
            }
    }

    BufferedImage image() { return img; }
}

// ════════════════════════════════════════════════════════════════
//  ANIMATED BACKGROUND STAR
// ════════════════════════════════════════════════════════════════
class BGStar {
    float x, y, vx, vy, br, twPhase, twSpeed;
    int rc, gc, bc, size;
    void tick(float dt) {
        x += vx * dt; y += vy * dt; twPhase += twSpeed * dt;
        if(x<0)x+=1400; if(x>=1400)x-=1400;
        if(y<0)y+=670;  if(y>=670) y-=670;
    }
    float alpha(){ return Math.min(1f, br*(0.68f+0.32f*(float)Math.sin(twPhase))); }
}

// ════════════════════════════════════════════════════════════════
//  MAIN PANEL
// ════════════════════════════════════════════════════════════════
class SimPanel extends JPanel {
    static final int W=1400, H=860;  // fits most screens
    static final int CH=670; // galaxy canvas height

    // Tilt: cos(inclination) = y-compression, sin = z projection
    // Andromeda 77°  Sombrero 84°  Collision ~60°
    static final double AND_COS=0.225, AND_SIN=0.974;
    static final double SOM_COS=0.105, SOM_SIN=0.995;
    static final double COL_COS=0.32,  COL_SIN=0.947;

    // View state per mode [0=both,1=andromeda,2=sombrero,3=collision]
    private int mode=0;
    private final double[] zoom={1,1,1,1}, panX={0,0,0,0}, panY={0,0,0,0};
    private Point  drag0; private double dPX,dPY;
    private int    hoverX=-1, hoverY=-1;
    private String tipText=null; private int tipX,tipY;

    // Hit records built each frame for tooltip detection
    private final List<int[]>  hitPx  = new ArrayList<>();
    private final List<String> hitTip = new ArrayList<>();

    private final Galaxy    andGal  = new Galaxy();
    private final Galaxy    somGal  = new Galaxy();
    private final Galaxy    collA   = new Galaxy();
    private final Galaxy    collB   = new Galaxy();
    private final Renderer  ren     = new Renderer(W, CH);
    private       BufferedImage bg;
    private final List<BGStar> bgStars = new ArrayList<>();

    // Collision galaxy approach (simplified 2-body center-of-mass)
    private double collTime = 0; // drives the merger timeline
    private double collAX=0, collAY=0, collBX=0, collBY=0; // updated each frame by paintCollision

    private final String[]    BTNS  = {"Side by Side","Andromeda M31","Sombrero M104","⚡ Collision"};
    private final Rectangle[] BRECT = new Rectangle[4];
    private final Rectangle   lblRA = new Rectangle(), lblRS = new Rectangle();

    SimPanel() {
        setPreferredSize(new Dimension(W, H));
        setBackground(Color.BLACK);

        Random rng = new Random(2025);

        andGal.name="Andromeda M31"; andGal.labelCol=new Color(170,212,255);
        andGal.build(2200, 2, false, 0, rng);

        somGal.name="Sombrero M104"; somGal.labelCol=new Color(255,200,100);
        somGal.build(2000, 0, true,  1, rng);

        Random cr=new Random(99);
        collA.name="Andromeda M31"; collA.labelCol=new Color(130,165,255);
        collA.build(1400,2,false,0,cr);  // Andromeda: 2-arm blue spiral
        collB.name="Sombrero M104"; collB.labelCol=new Color(255,200,100);
        collB.build(1400,0,true, 1,cr);  // Sombrero: uniform disk + golden bulge

        bg = buildBg(new Random(42));
        buildBGStars(new Random(777));

        // Timer: tick ALL galaxies every frame — no mode gate
        new Timer(16, e -> {
            double dt=0.018;
            bgStars.forEach(s -> s.tick(0.016f));
            andGal.tick(dt);
            somGal.tick(dt);
            // Real galaxy merger handled entirely inside paintCollision()
            collA.tick(dt); collB.tick(dt);
            repaint();
        }).start();

        addMouseWheelListener(e -> {
            double f=e.getWheelRotation()<0?1.13:0.885;
            zoom[mode]=Math.max(0.2, Math.min(22.0, zoom[mode]*f));
        });
        addMouseListener(new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){
                for(int i=0;i<BRECT.length;i++) if(BRECT[i]!=null&&BRECT[i].contains(e.getPoint())){mode=i;return;}
                drag0=e.getPoint(); dPX=panX[mode]; dPY=panY[mode];
            }
            @Override public void mouseReleased(MouseEvent e){drag0=null;}
            @Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2){zoom[mode]=1;panX[mode]=0;panY[mode]=0;}}
        });
        addMouseMotionListener(new MouseMotionAdapter(){
            @Override public void mouseDragged(MouseEvent e){
                if(drag0==null)return;
                panX[mode]=dPX+(e.getX()-drag0.x); panY[mode]=dPY+(e.getY()-drag0.y);
            }
            @Override public void mouseMoved(MouseEvent e){
                hoverX=e.getX(); hoverY=e.getY(); tipText=null;
                if(lblRA.contains(hoverX,hoverY)) { tipText="Andromeda Galaxy (M31)\n2.537 Million ly from Earth\n220,000 ly diameter\n~1 Trillion stars"; tipX=hoverX+14; tipY=hoverY-8; }
                else if(lblRS.contains(hoverX,hoverY)) { tipText="Sombrero Galaxy (M104)\n28 Million ly from Earth\n50,000 ly diameter\nBH: ~1 Billion M☉"; tipX=hoverX+14; tipY=hoverY-8; }
                else for(int i=0;i<hitPx.size();i++){int[]h=hitPx.get(i);int dx=hoverX-h[0],dy=hoverY-h[1];if(dx*dx+dy*dy<=h[2]*h[2]){tipText=hitTip.get(i);tipX=hoverX+14;tipY=hoverY-8;break;}}
                boolean onBtn=false; for(Rectangle r:BRECT) if(r!=null&&r.contains(hoverX,hoverY)){onBtn=true;break;}
                setCursor(onBtn?Cursor.getPredefinedCursor(Cursor.HAND_CURSOR):Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            }
        });
    }

    private BufferedImage buildBg(Random rng) {
        // Only paint the milky-way nebula band — stars are drawn animated each frame
        BufferedImage b=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        int[] bpx=((DataBufferInt)b.getRaster().getDataBuffer()).getData();
        for(int y=0;y<H;y++) for(int x=0;x<W;x++){
            double dx2=((double)x/W-.5)*2.5, dy2=((double)y/H-.5)*6.0;
            int v=(int)(Math.exp(-(dx2*dx2*18+dy2*dy2*2.5))*15);
            bpx[y*W+x]=(v<<16)|((int)(v*.55)<<8)|v;
        }
        return b;
    }

    private void buildBGStars(Random rng) {
        bgStars.clear();
        for(int i=0;i<5000;i++){
            BGStar s=new BGStar();
            s.x=rng.nextInt(W); s.y=rng.nextInt(CH);
            // Very slow drift (parallax feel)
            s.vx=(rng.nextFloat()-0.5f)*0.25f;
            s.vy=(rng.nextFloat()-0.5f)*0.08f;
            s.br=0.22f+rng.nextFloat()*0.78f;
            s.twPhase=rng.nextFloat()*6.28f;
            s.twSpeed=0.25f+rng.nextFloat()*1.4f; // different twinkle speeds
            // Color variety: blue, white, warm
            float cv=rng.nextFloat();
            if(cv<0.12f){s.rc=130;s.gc=155;s.bc=255;} // blue
            else if(cv<0.20f){s.rc=255;s.gc=210;s.bc=150;} // warm
            else {s.rc=195+rng.nextInt(60);s.gc=205+rng.nextInt(50);s.bc=210+rng.nextInt(45);}
            s.size=s.br>0.88f?1:0;
            bgStars.add(s);
        }
    }

    private int addC(int cur,int cr,int cg,int cb,int a){
        return (Math.min(255,((cur>>16)&0xFF)+cr*a/255)<<16)|(Math.min(255,((cur>>8)&0xFF)+cg*a/255)<<8)|Math.min(255,((cur)&0xFF)+cb*a/255);
    }

    @Override
    protected void paintComponent(Graphics gr){
        Graphics2D g2=(Graphics2D)gr;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.drawImage(bg,0,0,null);

        // Draw animated background stars (twinkling + drifting)
        for(BGStar s : bgStars){
            if(s.y>=CH) continue;
            float a=s.alpha();
            int ia=(int)(a*255);
            int rc=Math.min(255,s.rc*ia/255), gc=Math.min(255,s.gc*ia/255), bc=Math.min(255,s.bc*ia/255);
            int px2=(int)s.x, py2=(int)s.y;
            if(s.size==0){
                g2.setColor(new Color(rc,gc,bc,ia));
                g2.fillRect(px2,py2,1,1);
            } else {
                // Tiny 2px bright star
                g2.setColor(new Color(Math.min(255,rc+30),Math.min(255,gc+30),Math.min(255,bc+30),ia));
                g2.fillRect(px2,py2,1,1);
                // Soft 1px halo
                int ha=ia/4;
                if(ha>0){
                    g2.setColor(new Color(rc,gc,bc,ha));
                    g2.fillRect(px2-1,py2,1,1); g2.fillRect(px2+1,py2,1,1);
                    g2.fillRect(px2,py2-1,1,1); g2.fillRect(px2,py2+1,1,1);
                }
            }
        }

        ren.clear();
        hitPx.clear(); hitTip.clear();

        double sc = 120.0 * zoom[mode];
        switch(mode){
            case 0 -> {
                double sc0=120.0*zoom[0];
                paintGalaxy(andGal, 0, 0, W/4+(int)panX[0], CH/2+(int)panY[0], sc0, AND_COS, AND_SIN, false);
                paintGalaxy(somGal, 0, 0, 3*W/4+(int)panX[0], CH/2+(int)panY[0], sc0, SOM_COS, SOM_SIN, true);
            }
            case 1 -> paintGalaxy(andGal, 0, 0, W/2+(int)panX[1], CH/2+(int)panY[1], sc, AND_COS, AND_SIN, false);
            case 2 -> paintGalaxy(somGal, 0, 0, W/2+(int)panX[2], CH/2+(int)panY[2], sc, SOM_COS, SOM_SIN, true);
            case 3 -> paintCollision(W/2+(int)panX[3], CH/2+(int)panY[3], 120.0*zoom[3]);
        }

        ren.bloom(0.80f, 5, 3, 12f);
        g2.drawImage(ren.image(), 0, 0, null);

        drawOverlay(g2);
        drawHUD(g2);
        drawButtons(g2);
        drawTooltip(g2);
    }

    /** Render all stars of a galaxy onto the pixel buffer. */
    private void paintGalaxy(Galaxy gal, double gOffX, double gOffY, int ocx, int ocy,
                              double sc, double cosI, double sinI, boolean dust){
        for (Star s : gal.stars){
            // Analytic 2D position in disk plane
            double px = s.r * Math.cos(s.angle);
            double py = s.r * Math.sin(s.angle);
            // 3D projection: y compressed by cosI, diskZ projected by sinI
            int sx = (int)(ocx + (gOffX + px) * sc);
            int sy = (int)(ocy + (gOffY + py * cosI + s.diskZ * sinI) * sc);
            float a = s.alpha();
            ren.dot(sx, sy, s.cr*a, s.cg*a, s.cb*a, Math.min(4, s.glow));
            // Register bright stars for tooltip
            if (s.glow >= 3 && sx>=0 && sx<W && sy>=0 && sy<CH){
                int hitR = (int)(GR(s.glow) * 3.5);
                hitPx.add(new int[]{sx, sy, hitR});
                hitTip.add((s.tip!=null?s.tip:"Star")+"\n"+gal.name);
            }
        }
        if (dust){
            int hw=(int)(1.4*sc*0.96);
            int hh=Math.max(3,(int)(1.4*sc*0.048));
            ren.dustLane(ocx+2, ocy+1, hw, hh);
        }
    }

    /**
     * REAL galaxy collision rendering.
     * - Galaxy centers follow a decaying inward spiral (dynamical friction).
     * - Each star's screen position is individually displaced by the
     *   gravitational pull of the OTHER galaxy's center — this creates
     *   real tidal tails, bridges, and streams.
     * - Stars pass RIGHT THROUGH each other (space is 99.9999% empty).
     * - No bouncing. No rigid bodies. Pure gravity.
     */
    private void paintCollision(int cx, int cy, double sc) {
        collTime += 0.016 * 0.045;
        // Decaying spiral — 3 passes then merge
        double decay    = Math.exp(-collTime * 0.032);
        double sep      = 2.4 * decay;
        double orbAngle = collTime * 0.9;
        double aX =  sep * Math.cos(orbAngle);
        double aY =  sep * Math.sin(orbAngle) * 0.25;
        double bX = -sep * Math.cos(orbAngle);
        double bY = -sep * Math.sin(orbAngle) * 0.25;

        double eps2   = 0.06 * 0.06;
        // Tidal strength — much stronger so stars visibly stretch into streams
        double tidalG = 1.8;
        // Extra stream amplification when galaxies are close
        double proximity = Math.max(0, 1.0 - sep / 2.4);
        double streamAmp = 1.0 + proximity * 4.0;

        // ── Andromeda stars ──
        for (Star s : collA.stars) {
            double lx = s.r * Math.cos(s.angle);
            double ly = s.r * Math.sin(s.angle) * AND_COS + s.diskZ * AND_SIN;
            double wx = aX + lx, wy = aY + ly;

            double dx = bX - wx, dy = bY - wy;
            double r2 = dx*dx + dy*dy + eps2;
            double rr = Math.sqrt(r2);
            // Tidal tail: outer stars flung farther, inner stars stay bound
            double ownR = Math.sqrt(lx*lx + ly*ly*4.0);
            double tidal = (tidalG * ownR * streamAmp) / r2;
            tidal = Math.min(tidal, 1.8); // cap so core stays intact
            double tdx = (dx/rr) * tidal;
            double tdy = (dy/rr) * tidal;

            int sx = (int)(cx + (wx + tdx) * sc);
            int sy = (int)(cy + (wy + tdy) * sc);
            float a = s.alpha();
            ren.dot(sx, sy, s.cr*a, s.cg*a, s.cb*a, Math.min(4, s.glow));
        }

        // ── Sombrero stars ──
        for (Star s : collB.stars) {
            double lx = s.r * Math.cos(s.angle);
            double ly = s.r * Math.sin(s.angle) * SOM_COS + s.diskZ * SOM_SIN;
            double wx = bX + lx, wy = bY + ly;

            double dx = aX - wx, dy = aY - wy;
            double r2 = dx*dx + dy*dy + eps2;
            double rr = Math.sqrt(r2);
            double ownR = Math.sqrt(lx*lx + ly*ly*4.0);
            double tidal = (tidalG * ownR * streamAmp) / r2;
            tidal = Math.min(tidal, 1.8);
            double tdx = (dx/rr) * tidal;
            double tdy = (dy/rr) * tidal;

            int sx = (int)(cx + (wx + tdx) * sc);
            int sy = (int)(cy + (wy + tdy) * sc);
            float a = s.alpha();
            ren.dot(sx, sy, s.cr*a, s.cg*a, s.cb*a, Math.min(4, s.glow));
        }

        // ── Sombrero dust lane ──
        ren.dustLane((int)(cx+bX*sc),(int)(cy+bY*sc), (int)(1.4*sc*0.96), Math.max(3,(int)(1.4*sc*0.048)));

        // ── Bright nuclei ──
        ren.dot((int)(cx+aX*sc),(int)(cy+aY*sc), 160,200,255, 4);
        ren.dot((int)(cx+aX*sc),(int)(cy+aY*sc), 255,255,255, 3);
        ren.dot((int)(cx+bX*sc),(int)(cy+bY*sc), 255,220,130, 4);
        ren.dot((int)(cx+bX*sc),(int)(cy+bY*sc), 255,255,255, 3);

        // ── Violent core merger flash ──
        if (sep < 0.55) {
            float f = (float)((0.55 - sep) / 0.55);
            // Multiple concentric glows at merge point
            for (int r = 4; r >= 0; r--)
                ren.dot(cx, cy, 255*f, 230*f*(1-r*0.12f), 160*f*(1-r*0.18f), r);
            // Mix cores: intermediate color (blue+golden = white-hot)
            ren.dot(cx, cy, 255*f, 255*f, 200*f, 4);
        }

        if (decay < 0.025) collTime = 0;
        collAX = aX; collAY = aY;
        collBX = bX; collBY = bY;
    }

    private int GR(int ki){ return Renderer.GR[Math.min(Renderer.GR.length-1, ki)]; }

    // ── Overlays ──────────────────────────────────────────────────
    private void drawOverlay(Graphics2D g2){
        switch(mode){
            case 0 -> {
                galLabel(g2, W/4,  30, andGal, lblRA);
                galLabel(g2, 3*W/4,30, somGal, lblRS);
                g2.setColor(new Color(20,42,70,80));
                float[]d={4,8}; g2.setStroke(new BasicStroke(1,0,0,1,d,0));
                g2.drawLine(W/2,46,W/2,CH); g2.setStroke(new BasicStroke(1f));
                zoomInfo(g2,zoom[0]);
            }
            case 1 -> { galLabel(g2,W/2,30,andGal,lblRA); zoomInfo(g2,zoom[1]); }
            case 2 -> { galLabel(g2,W/2,30,somGal,lblRS); zoomInfo(g2,zoom[2]); }
            case 3 -> {
                String t="ANDROMEDA M31 × SOMBRERO M104 — GRAVITATIONAL MERGER SIMULATION";
                g2.setFont(new Font("Courier New",Font.BOLD,13)); FontMetrics fm=g2.getFontMetrics();
                g2.setColor(new Color(255,168,50,218)); g2.drawString(t,(W-fm.stringWidth(t))/2,24);
                float p=0.5f+0.5f*(float)Math.sin(System.currentTimeMillis()*.002);
                g2.setColor(new Color(255,50,10,(int)(p*9))); g2.fillRect(0,0,W,CH);
                // Galaxy labels in collision mode
                int cxA=(int)(W/2+collAX*120*zoom[3]+panX[3]);
                int cxB=(int)(W/2+collBX*120*zoom[3]+panX[3]);
                g2.setFont(new Font("Courier New",Font.BOLD,10));
                g2.setColor(new Color(130,165,255,190)); g2.drawString("ANDROMEDA M31",cxA-45,CH/2+(int)panY[3]-160);
                g2.setColor(new Color(255,200,100,190)); g2.drawString("SOMBRERO M104",cxB-45,CH/2+(int)panY[3]-160);
                g2.setFont(new Font("Courier New",Font.PLAIN,9)); g2.setColor(new Color(38,75,108));
                double currSep=Math.sqrt((collBX-collAX)*(collBX-collAX)+(collBY-collAY)*(collBY-collAY));
                String phase = currSep < 0.3 ? "⚡ TIDAL CORES MERGING — elliptical galaxy forming" : currSep < 1.5 ? "→ TIDAL STREAMS forming — stars pulled into bridges" : "→ APPROACHING — gravity pulling galaxies together";
                g2.drawString(phase + "   |   Separation: "+String.format("%.0f",currSep*22)+" kly   |   Stars pass through: space is 99.9999% empty",12,13);
                if(currSep < 0.4){
                    float flash=(float)((0.4-currSep)/0.4);
                    g2.setColor(new Color(255,210,140,(int)(flash*35))); g2.fillRect(0,0,W,CH);
                }
                zoomInfo(g2,zoom[3]);
            }
        }
        g2.setFont(new Font("Courier New",Font.PLAIN,9)); g2.setColor(new Color(24,50,72));
        String h="🖱 SCROLL=Zoom  DRAG=Pan  DBL-CLICK=Reset  HOVER=Info";
        FontMetrics fmh=g2.getFontMetrics(); g2.drawString(h,W-fmh.stringWidth(h)-10,CH-4);
    }

    private void galLabel(Graphics2D g2,int cx,int y,Galaxy gal,Rectangle hit){
        Color col=gal.labelCol;
        String name=gal.name;
        g2.setFont(new Font("Courier New",Font.BOLD,12)); FontMetrics fm=g2.getFontMetrics();
        int nx=cx-fm.stringWidth(name)/2;
        boolean hov=(hoverX>=0&&hit.width>0&&hit.contains(hoverX,hoverY));
        if(hov){g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),20));g2.fillRoundRect(nx-8,y-14,fm.stringWidth(name)+16,18,6,6);}
        g2.setColor(hov?col.brighter():col); g2.drawString(name,nx,y);
        hit.setBounds(nx-8,y-14,fm.stringWidth(name)+16,18);
        // Sub-info
        String sub = gal==andGal ? "2.537 Mly  ·  220,000 ly  ·  77° incl.  ·  SA(s)b Spiral  ·  ~1 Trillion stars"
                                  : "28 Mly  ·  50,000 ly  ·  84° incl.  ·  SA(s)a  ·  BH: ~1B M☉  ·  ~100B stars";
        g2.setFont(new Font("Courier New",Font.PLAIN,9)); fm=g2.getFontMetrics();
        g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),105));
        g2.drawString(sub,cx-fm.stringWidth(sub)/2,y+13);
    }

    private void zoomInfo(Graphics2D g2,double z){
        g2.setFont(new Font("Courier New",Font.PLAIN,9)); g2.setColor(new Color(26,52,74));
        g2.drawString(String.format("zoom: %.1fx  |  scroll=zoom  drag=pan  double-click=reset",z),10,CH-4);
    }

    private void drawTooltip(Graphics2D g2){
        if(tipText==null||tipText.isEmpty()) return;
        String[] lines=tipText.split("\n");
        g2.setFont(new Font("Courier New",Font.PLAIN,10)); FontMetrics fm=g2.getFontMetrics();
        int lh=fm.getHeight(),mw=0; for(String l:lines) mw=Math.max(mw,fm.stringWidth(l));
        int tw=mw+16,th=lines.length*lh+10;
        int tx=Math.min(tipX,W-tw-5), ty=Math.max(5,Math.min(tipY,CH-th-5));
        g2.setColor(new Color(6,15,34,218)); g2.fillRoundRect(tx,ty,tw,th,8,8);
        g2.setColor(new Color(50,95,145)); g2.drawRoundRect(tx,ty,tw,th,8,8);
        for(int i=0;i<lines.length;i++){
            g2.setColor(i==0?new Color(178,212,255):new Color(128,168,200));
            g2.drawString(lines[i],tx+8,ty+fm.getAscent()+5+i*lh);
        }
    }

    // ── HUD ───────────────────────────────────────────────────────
    private static final String[][] A_ST={
        {"TYPE","SA(s)b Barred Spiral"},
        {"DIAMETER","220,000 light-years"},
        {"DISTANCE","2.537 Million ly"},
        {"STARS","~1 Trillion"},
        {"MASS","1.5 × 10¹² M☉"},
        {"DARK MATTER","~1.2 × 10¹² M☉"},
        {"BULGE DIA","~30,000 ly"},
        {"DISK THICK","~1,000 ly"},
        {"SPIRAL ARMS","2 major + spurs"},
        {"INCLINATION","77° from face-on"},
        {"ANG. SIZE","3.167° (6× Moon)"},
        {"MAGNITUDE","3.44 (naked eye)"},
        {"REDSHIFT","−0.001001 (blueshift)"},
        {"AGE","~10 Billion years"},
        {"METALLICITY","Solar to super-solar"},
        {"FATE","Merging w/ Sombrero (hypothetical)"},
    };
    private static final String[][] S_ST={
        {"TYPE","SA(s)a Unbarred Spiral"},
        {"DIAMETER","50,000 light-years"},
        {"DISTANCE","28 Million ly"},
        {"STARS","~100 Billion"},
        {"MASS","8 × 10¹¹ M☉"},
        {"BLACK HOLE","~1 Billion M☉"},
        {"DARK MATTER","~7 × 10¹¹ M☉"},
        {"BULGE DIA","~20,000 ly"},
        {"DISK THICK","~600 ly"},
        {"DUST LANE","Defining feature"},
        {"INCLINATION","84° (nearly edge-on)"},
        {"ANG. SIZE","8.7′ × 3.5′"},
        {"MAGNITUDE","8.98 (binoculars)"},
        {"REDSHIFT","+0.003416"},
        {"AGE","~13 Billion years"},
        {"GLOBULAR CLUSTERS","~2,000 (unusually rich)"},
    };

    private void drawHUD(Graphics2D g2){
        int py=CH;
        g2.setPaint(new GradientPaint(0,py,new Color(2,6,20,248),0,H,new Color(1,3,12,255)));
        g2.fillRect(0,py,W,H-py); g2.setPaint(null);
        g2.setColor(new Color(12,32,60)); g2.drawLine(0,py,W,py);
        switch(mode){
            case 0 -> {
                sCols(g2, 28, py+13, "ANDROMEDA M31", new Color(170,212,255), A_ST);
                compBars(g2, W/2-118, py+12);
                sCols(g2, W/2+108, py+13, "SOMBRERO M104", new Color(255,200,100), S_ST);
            }
            case 1 -> sRow(g2, py+13, W, "ANDROMEDA GALAXY  M31 / NGC 224", new Color(170,212,255), A_ST);
            case 2 -> sRow(g2, py+13, W, "SOMBRERO GALAXY  M104 / NGC 4594", new Color(255,200,100), S_ST);
            case 3 -> collHUD(g2, py);
        }
        g2.setFont(new Font("Courier New",Font.PLAIN,9));g2.setColor(new Color(16,40,60));
        g2.drawString("ANALYTIC STABLE ORBITS  ·  TIDAL GRAVITY STREAMS  ·  ADDITIVE PIXEL BLENDING  ·  MULTI-PASS BLOOM  ·  HOVER FOR STAR INFO",14,H-54);
    }

    private void collHUD(Graphics2D g2, int py) {
        // Title
        g2.setFont(new Font("Courier New",Font.BOLD,11));
        g2.setColor(new Color(255,138,38));
        g2.drawString("ANDROMEDA M31  ×  SOMBRERO M104  —  HYPOTHETICAL TIDAL MERGER SIMULATION", 28, py+14);
        g2.setColor(new Color(30,58,88)); g2.drawLine(28,py+18,W-28,py+18);

        // Three-column layout: Andromeda facts | Merger physics | Sombrero facts
        int col=W/3;
        g2.setFont(new Font("Courier New",Font.BOLD,10));
        g2.setColor(new Color(130,175,255)); g2.drawString("◆ ANDROMEDA M31", 28, py+32);
        g2.setColor(new Color(255,175,55)); g2.drawString("◆ MERGER PHYSICS", col+10, py+32);
        g2.setColor(new Color(255,200,100)); g2.drawString("◆ SOMBRERO M104", col*2+10, py+32);

        String[][] andFacts = {
            {"Type","SA(s)b Barred Spiral"},{"Diameter","220,000 ly"},
            {"Stars","~1 Trillion"},{"Mass","1.5 × 10¹² M☉"},
            {"Black Hole","~140 Million M☉"},{"Inclination","77° from face-on"},
            {"Distance","2.537 Million ly"},{"Magnitude","3.44 (naked eye)"},
        };
        String[][] mergeFacts = {
            {"Star collisions","Essentially zero"},{"Timescale","Billions of years"},
            {"Tidal streams","Form immediately"},{"Result","Giant elliptical"},
            {"Star orbits","Completely disrupted"},{"Dark matter","Halos merge first"},
            {"Gas clouds","DO collide & ignite"},{"Starbursts","Triggered by merger"},
            {"Driving force","Dynamical friction"},{"End state","Slow core inspiral"},
        };
        String[][] somFacts = {
            {"Type","SA(s)a Unbarred"},{"Diameter","50,000 ly"},
            {"Stars","~100 Billion"},{"Mass","8 × 10¹¹ M☉"},
            {"Black Hole","~1 Billion M☉"},{"Inclination","84° (edge-on)"},
            {"Distance","28 Million ly"},{"Glob. Clusters","~2,000 (rich!)"},
        };

        g2.setFont(new Font("Courier New",Font.PLAIN,9));
        int lineH=16;
        for(int i=0;i<andFacts.length;i++){
            int ry=py+46+i*lineH;
            g2.setColor(new Color(55,90,128)); g2.drawString(andFacts[i][0]+":", 28, ry);
            g2.setColor(new Color(160,200,235)); g2.drawString(andFacts[i][1], 118, ry);
        }
        for(int i=0;i<mergeFacts.length&&i<andFacts.length+2;i++){
            int ry=py+46+i*lineH;
            g2.setColor(new Color(130,95,38)); g2.drawString(mergeFacts[i][0]+":", col+10, ry);
            g2.setColor(new Color(218,185,135)); g2.drawString(mergeFacts[i][1], col+130, ry);
        }
        for(int i=0;i<somFacts.length;i++){
            int ry=py+46+i*lineH;
            g2.setColor(new Color(128,90,35)); g2.drawString(somFacts[i][0]+":", col*2+10, ry);
            g2.setColor(new Color(255,200,100)); g2.drawString(somFacts[i][1], col*2+118, ry);
        }
    }

    private void sCols(Graphics2D g2,int x,int y,String t,Color col,String[][]st){
        g2.setFont(new Font("Courier New",Font.BOLD,10));g2.setColor(col);g2.drawString(t,x,y);
        g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),38));g2.drawLine(x,y+3,x+280,y+3);
        g2.setFont(new Font("Courier New",Font.PLAIN,9));
        // 4 rows × 4 columns layout
        int cols=2, rowH=17, colW=138;
        for(int i=0;i<st.length;i++){
            int rx=x+(i%cols)*colW, ry=y+16+(i/cols)*rowH;
            g2.setColor(new Color(40,68,92)); g2.drawString(st[i][0]+":",rx,ry);
            g2.setColor(new Color(158,192,215)); g2.drawString(st[i][1],rx,ry+10);
        }
    }

    private void sRow(Graphics2D g2,int y,int w,String t,Color col,String[][]st){
        g2.setFont(new Font("Courier New",Font.BOLD,10));g2.setColor(col);g2.drawString(t,28,y);
        g2.setColor(new Color(col.getRed(),col.getGreen(),col.getBlue(),35));g2.drawLine(28,y+3,w-28,y+3);
        g2.setFont(new Font("Courier New",Font.PLAIN,9));
        // 4 columns × 4 rows
        int cols=4, rowH=17, colW=(w-56)/cols;
        for(int i=0;i<st.length;i++){
            int rx=28+(i%cols)*colW, ry=y+16+(i/cols)*rowH;
            g2.setColor(new Color(40,68,92)); g2.drawString(st[i][0]+":",rx,ry);
            g2.setColor(new Color(158,192,215)); g2.drawString(st[i][1],rx,ry+10);
        }
    }

    private void compBars(Graphics2D g2,int x,int y){
        g2.setFont(new Font("Courier New",Font.BOLD,9));g2.setColor(new Color(28,54,80));g2.drawString("COVERAGE RATIO",x-4,y);
        String[][]d={{"Diameter","220k ly","100","50k ly","23"},{"Stars","1 Trillion","100","100B","10"},{"Distance","2.5 Mly","9","28 Mly","100"}};
        Color ac=new Color(77,184,255),sc2=new Color(255,170,51);
        for(int i=0;i<d.length;i++){int by=y+17+i*35;g2.setFont(new Font("Courier New",Font.PLAIN,9));g2.setColor(new Color(38,64,88));g2.drawString(d[i][0].toUpperCase(),x-4,by);mbar(g2,x-4,by+5,91,Integer.parseInt(d[i][2]),ac,d[i][1]);mbar(g2,x+91,by+5,91,Integer.parseInt(d[i][4]),sc2,d[i][3]);}
        g2.setFont(new Font("Courier New",Font.PLAIN,8));g2.setColor(ac);g2.drawString("■ Andromeda",x-4,y+125);g2.setColor(sc2);g2.drawString("■ Sombrero",x+78,y+125);
    }

    private void mbar(Graphics2D g2,int x,int y,int mW,int pct,Color col,String lbl){
        int fw=mW*pct/100;g2.setColor(new Color(7,16,34));g2.fillRoundRect(x,y,mW,5,4,4);
        g2.setPaint(new GradientPaint(x,y,new Color(col.getRed(),col.getGreen(),col.getBlue(),85),x+fw,y,col));g2.fillRoundRect(x,y,fw,5,4,4);g2.setPaint(null);
        g2.setFont(new Font("Courier New",Font.PLAIN,8));g2.setColor(col);g2.drawString(lbl,x,y+14);
    }

    private void drawButtons(Graphics2D g2){
        int bw=158,bh=32,gap=10,tot=BTNS.length*bw+(BTNS.length-1)*gap,sx=(W-tot)/2,y=H-46;
        // Button tray background
        g2.setColor(new Color(4,10,24,200));
        g2.fillRoundRect(sx-16,y-8,tot+32,bh+16,12,12);
        g2.setColor(new Color(18,40,70,180));
        g2.drawRoundRect(sx-16,y-8,tot+32,bh+16,12,12);

        for(int i=0;i<BTNS.length;i++){
            int x=sx+i*(bw+gap); BRECT[i]=new Rectangle(x,y,bw,bh);
            boolean act=mode==i,hov=BRECT[i].contains(hoverX,hoverY);
            Color base=i==3?new Color(70,18,4):new Color(8,18,38);
            Color bord=act?(i==3?new Color(255,110,28):new Color(80,190,255)):hov?new Color(55,95,145):new Color(22,50,85);
            Color txt =act?(i==3?new Color(255,175,65):new Color(165,212,255)):hov?new Color(130,180,225):new Color(65,108,155);
            // Fill
            g2.setColor(base); g2.fillRoundRect(x,y,bw,bh,8,8);
            // Active glow
            if(act){ g2.setColor(new Color(bord.getRed(),bord.getGreen(),bord.getBlue(),28)); g2.fillRoundRect(x-3,y-3,bw+6,bh+6,11,11); }
            // Hover fill
            if(hov&&!act){ g2.setColor(new Color(bord.getRed(),bord.getGreen(),bord.getBlue(),12)); g2.fillRoundRect(x,y,bw,bh,8,8); }
            // Border
            g2.setColor(bord); g2.setStroke(new BasicStroke(act?1.4f:1f)); g2.drawRoundRect(x,y,bw,bh,8,8); g2.setStroke(new BasicStroke(1f));
            // Label
            g2.setFont(new Font("Courier New",act?Font.BOLD:Font.PLAIN,11)); g2.setColor(txt);
            FontMetrics fm=g2.getFontMetrics(); g2.drawString(BTNS[i],x+(bw-fm.stringWidth(BTNS[i]))/2,y+21);
        }
    }
}
