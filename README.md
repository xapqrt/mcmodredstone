# Redstone to Real

A Minecraft mod + companion desktop app that translates redstone circuits into real logic gate diagrams (AND, OR, NOT, flip-flops). Export as a circuit schematic image or even generate Arduino code from your redstone builds.

## How to Test

### 1. In-game extraction (Mod)
1. Run \`./gradlew build\` to compile the Fabric mod for 1.20+.
2. Launch Minecraft with Fabric.
3. Build a redstone circuit sequence (torches, wires, comparators, repeaters).
4. Run the command \`/scanredstone\`.
5. The subgraph DAG will be generated and saved to \`redstone_graph.json\` in your game run directory.

### 2. Isomorphic Matcher and App (Web)
1. Open \`webapp/index.html\` in a browser.
2. Click "Choose File" and upload the \`redstone_graph.json\` (we included a sample in the \`webapp\` folder!).
3. Click **"Run Isomorphic Matcher"** to see your logic gates properly mapped to an SVG visual layout representing NAND, OR, AND, NOR, etc.
4. Click **"Generate Arduino Code"** to transpile the physical graph to C++ logic. The output text block will have standard physical logic to upload to an Arduino!

## Supported Components Let's Fix It All
- **NOT** = 1 In -> NOT_GATE
- **AND/NAND** = 2+ In -> Multiple NOT_GATES or COMPARATOR
- **BUFFER** = Repeater (includes parameterized translation of delay ticks to literal C++ `delay()`)
- **LEVER/BUTTON** = Input switches mapped to pullup pins.

