# 🌌 Galaxy Simulation

A Java Swing-based interactive galaxy simulation featuring analytic stable orbits, bloom rendering, and multiple galaxy modes.

![Java](https://img.shields.io/badge/Java-8%2B-orange?logo=java)
![License](https://img.shields.io/badge/license-MIT-blue)

---

## ✨ Features

- **Analytic Orbit Integration** — Stars never scatter or escape; orbits are mathematically stable
- **Density-Wave Spiral Arms** — Arms emerge naturally as slower orbital regions
- **Sombrero Galaxy** — Rendered with full 3D hat shape and a dust lane
- **Bloom Rendering** — Additive pixel blending with multi-pass bloom for a realistic glow effect
- **Hover Tooltips** — Hover over stars and galaxy labels for detailed info
- **4 Simulation Modes:**
  | Mode | Description |
  |------|-------------|
  | Side-by-Side | Compare Andromeda (M31) and Sombrero (M104) |
  | Andromeda | Full-screen Andromeda Galaxy view |
  | Sombrero | Full-screen Sombrero Galaxy view |
  | ⚡ Collision | Hypothetical tidal merger simulation |

---

## 🚀 Getting Started

### Prerequisites
- Java 8 or higher

### Run the Simulation

```bash
# Compile
javac src/GalaxySimulation.java -d out

# Run
java -cp out GalaxySimulation
```

---

## 🎮 Controls

| Action | Control |
|--------|---------|
| Zoom | Scroll wheel |
| Pan | Click and drag |
| Reset view | Double-click |
| Switch mode | Click mode buttons |
| Star info | Hover over a star |

---

## 🌠 Galaxy Data

### Andromeda (M31)
- Type: SA(s)b Barred Spiral
- Diameter: ~220,000 light-years
- Stars: ~1 Trillion
- Distance: 2.537 Million ly

### Sombrero (M104)
- Type: SA(s)a Unbarred Spiral
- Diameter: ~50,000 light-years
- Stars: ~100 Billion
- Distance: 28 Million ly
- Black Hole: ~1 Billion solar masses

---

## 📁 Project Structure

```
GalaxySimulation/
├── src/
│   └── GalaxySimulation.java   # Main simulation source
├── .gitignore
└── README.md
```

---

## 📄 License

This project is licensed under the MIT License.
