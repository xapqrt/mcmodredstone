// Web app ingestion and isomorphic matching
let graph_nodes = null;
let collapsed_nodes = [];

// Sample DAG export from the Fabric Mod
let sampleJSON = {
    nodes: [
        { id: "n1", type: "NOT_GATE" },
        { id: "n2", type: "NOT_GATE" },
        { id: "n3", type: "NOT_GATE" }
    ],
    edges: [
        { from: "n1", to: "n3" },
        { from: "n2", to: "n3" }
    ]
};

function detectSubGraphs(dag) {
    if (!dag || !dag.nodes) return [];
    console.log("Running Sub-Graph Isomorphism and Layout...");
    collapsed_nodes = [];
    
    // In edges and Out edges maps
    let inEdges = {};
    let outEdges = {};
    dag.nodes.forEach(n => {
        inEdges[n.id] = [];
        outEdges[n.id] = [];
    });
    if (dag.edges) {
        dag.edges.forEach(e => {
            let from = e.from || e.source;
            let to = e.to || e.target;
            if (inEdges[to] !== undefined && outEdges[from] !== undefined) {
                inEdges[to].push(from);
                outEdges[from].push(to);
            }
        });
    }

    // Topological depth calculation to prevent layout overlaps and ensure correct order
    let depths = {};
    dag.nodes.forEach(n => {
        depths[n.id] = 0;
    });

    // Relax paths up to 15 times to compute proper depth (handles loops safely by caching and capping)
    for (let iter = 0; iter < 15; iter++) {
        let changed = false;
        if (dag.edges) {
            dag.edges.forEach(e => {
                let from = e.from || e.source;
                let to = e.to || e.target;
                if (depths[from] !== undefined && depths[to] !== undefined) {
                    if (depths[to] <= depths[from]) {
                        depths[to] = depths[from] + 1;
                        changed = true;
                    }
                }
            });
        }
        if (!changed) break;
    }

    let and_counter = 0;
    let or_counter = 0;
    let not_counter = 0;
    let nand_counter = 0;
    let delay_counter = 0;
    let comp_counter = 0;
    let sr_counter = 0;

    let processed = new Set();

    dag.nodes.forEach(node => {
        if (processed.has(node.id)) return;

        // If the node type is already pre-collapsed by the Java backend
        if (node.type === "AND") {
            collapsed_nodes.push({ id: node.id, type: "AND", sources: inEdges[node.id], originalId: node.id, depth: depths[node.id] });
            processed.add(node.id);
        } else if (node.type === "SR_LATCH") {
            collapsed_nodes.push({ id: node.id, type: "SR_LATCH", sources: inEdges[node.id], originalId: node.id, depth: depths[node.id] });
            processed.add(node.id);
        } else if (node.type === "NOT") {
            collapsed_nodes.push({ id: node.id, type: "NOT", sources: inEdges[node.id], originalId: node.id, depth: depths[node.id] });
            processed.add(node.id);
        } else if (node.type === "DELAY") {
            let delay = (parseInt(node.delay) || 1) * 100;
            collapsed_nodes.push({ id: node.id, type: "DELAY", delay_ms: delay, sources: inEdges[node.id], originalId: node.id, depth: depths[node.id] });
            processed.add(node.id);
        } else if (node.type === "COMPARATOR") {
            if (node.mode === "subtract") {
                collapsed_nodes.push({ id: node.id, type: "XOR", sources: inEdges[node.id], originalId: node.id, depth: depths[node.id] });
            } else {
                collapsed_nodes.push({ id: node.id, type: "AND", sources: inEdges[node.id], originalId: node.id, depth: depths[node.id] });
            }
            processed.add(node.id);
        } else if (node.type === "INPUT") {
            collapsed_nodes.push({ id: node.id, type: "INPUT", sources: [], originalId: node.id, depth: depths[node.id] });
            processed.add(node.id);
        }
        // Fallback checks for raw (legacy or manually uploaded raw JSONs)
        else if (node.type === "NOT_GATE") {
            let ins = inEdges[node.id];
            if (ins.length >= 2 && ins.every(i => dag.nodes.find(n => n.id === i)?.type === "NOT_GATE")) {
                collapsed_nodes.push({ id: "NAND_" + nand_counter++, type: "NAND", sources: ins, originalId: node.id, depth: depths[node.id] });
            } else {
                collapsed_nodes.push({ id: "NOT_" + not_counter++, type: "NOT", sources: ins, originalId: node.id, depth: depths[node.id] });
            }
            processed.add(node.id);
        } else if (node.type === "BUFFER") {
            let delay = (parseInt(node.delay) || 1) * 100;
            collapsed_nodes.push({ id: "DELAY_" + delay_counter++, type: "DELAY", delay_ms: delay, sources: inEdges[node.id], originalId: node.id, depth: depths[node.id] });
            processed.add(node.id);
        }
    });

    // Apply layout positions
    let depthCounts = {};
    collapsed_nodes.forEach(gate => {
        let d = gate.depth || 0;
        if (depthCounts[d] === undefined) {
            depthCounts[d] = 0;
        }
        gate.x = (d * 180) + 80;
        gate.y = (depthCounts[d] * 100) + 60;
        depthCounts[d]++;
    });

    return collapsed_nodes;
}

function renderSVG(gates) {
    const container = document.getElementById("svg_container");
    let svgContent = `<svg width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">`;
    
    // Draw edges
    gates.forEach(gate => {
        if (gate.sources) {
            gate.sources.forEach(srcOriginalId => {
                let fromGate = gates.find(g => g.id === srcOriginalId || g.originalId === srcOriginalId);
                if (fromGate) {
                    let startX = fromGate.x + 50;
                    let startY = fromGate.y + 20;
                    let endX = gate.x;
                    let endY = gate.y + 20;
                    let ctrlX1 = startX + 40;
                    let ctrlX2 = endX - 40;
                    svgContent += `<path d="M ${startX},${startY} C ${ctrlX1},${startY} ${ctrlX2},${endY} ${endX},${endY}" fill="none" stroke="#e74c3c" stroke-width="2" />`;
                }
            });
        }
    });

    // Draw gates
    gates.forEach(gate => {
        let label = gate.id;
        if (gate.type === "NAND") {
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <path d="M 0,0 L 20,0 A 20,20 0 0,1 20,40 L 0,40 Z" fill="#2c3e50" stroke="white" stroke-width="2"/>
                    <circle cx="45" cy="20" r="5" fill="#2c3e50" stroke="white" stroke-width="2"/>
                    <text x="5" y="25" fill="white" font-size="10">NAND</text>
                    <text x="0" y="-5" fill="#bdc3c7" font-size="9">${label}</text>
                </g>
            `;
        } else if (gate.type === "SR_LATCH") {
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <rect x="0" y="0" width="60" height="60" fill="#2c3e50" stroke="white" stroke-width="2"/>
                    <text x="5" y="20" fill="white" font-size="12">S</text>
                    <text x="5" y="50" fill="white" font-size="12">R</text>
                    <text x="45" y="20" fill="white" font-size="12">Q</text>
                    <text x="40" y="50" fill="white" font-size="12">Q'</text>
                    <text x="0" y="-5" fill="#bdc3c7" font-size="9">${label}</text>
                </g>
            `;
        } else if (gate.type === "AND") {
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <path d="M 0,0 L 25,0 A 20,20 0 0,1 25,40 L 0,40 Z" fill="#2c3e50" stroke="white" stroke-width="2"/>
                    <text x="5" y="25" fill="white" font-size="12">AND</text>
                    <text x="0" y="-5" fill="#bdc3c7" font-size="9">${label}</text>
                </g>
            `;
        } else if (gate.type === "OR") {
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <path d="M 0,0 Q 15,20 0,40 Q 25,40 40,20 Q 25,0 0,0" fill="#2c3e50" stroke="white" stroke-width="2"/>
                    <text x="10" y="25" fill="white" font-size="12">OR</text>
                    <text x="0" y="-5" fill="#bdc3c7" font-size="9">${label}</text>
                </g>  
            `;
        } else if (gate.type === "NOT") {
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <polygon points="0,0 30,20 0,40" fill="#2c3e50" stroke="white" stroke-width="2"/>
                    <circle cx="35" cy="20" r="5" fill="#2c3e50" stroke="white" stroke-width="2"/>
                    <text x="5" y="25" fill="white" font-size="10">NOT</text>
                    <text x="0" y="-5" fill="#bdc3c7" font-size="9">${label}</text>
                </g>
            `;
        } else if (gate.type === "DELAY") {
             svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <rect x="0" y="5" width="50" height="30" fill="#2980b9" stroke="white" stroke-width="2"/>
                    <text x="5" y="25" fill="white" font-size="10">DELAY</text>
                    <text x="0" y="-5" fill="#bdc3c7" font-size="9">${label}</text>
                </g>
            `;
        } else if (gate.type === "INPUT") {
             svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <circle cx="20" cy="20" r="15" fill="#27ae60" stroke="white" stroke-width="2"/>
                    <text x="12" y="24" fill="white" font-size="10">IN</text>
                    <text x="0" y="-5" fill="#bdc3c7" font-size="9">${label}</text>
                </g>
            `;
        } else {
             svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <rect x="0" y="5" width="50" height="30" fill="#8e44ad" stroke="white" stroke-width="2"/>
                    <text x="5" y="25" fill="white" font-size="10">${gate.type}</text>
                    <text x="0" y="-5" fill="#bdc3c7" font-size="9">${label}</text>
                </g>
            `;
        }
    });
    
    svgContent += `</svg>`;
    container.innerHTML = svgContent;
}

function synthesize() {
    if (!graph_nodes) {
        graph_nodes = sampleJSON;
    }
    let logicGates = detectSubGraphs(graph_nodes);
    renderSVG(logicGates);
}

function generateCPP() {
    console.log("Transpiling DAG to C++ Arduino code...");
    let cpp = `// Auto-generated from Redstone-to-Real\n\n`;
    cpp += `void setup() {\n`;
    cpp += `  Serial.begin(9600);\n`;

    let pinCounter = 2; 
    let inputArray = {};
    if (collapsed_nodes && collapsed_nodes.length > 0) {
        let inputs = collapsed_nodes.filter(n => n.type === "INPUT");
        inputs.forEach((inp, idx) => {
            let pin = pinCounter + idx;
            cpp += `  pinMode(${pin}, INPUT_PULLUP);\n`;
            inputArray[inp.id] = pin;   
        });
    }
    
    cpp += `}\n\n`;
    cpp += `void loop() {\n`;
    cpp += `  // reading physical switches into memory\n`;
    for (const [id, pin] of Object.entries(inputArray)) {
        let safeId = id.replace(/[^a-zA-Z0-9]/g, '_');
        cpp += `  bool var_${safeId} = !digitalRead(${pin}); // active low pullup\n`;
    }
    
    cpp += `\n  // evaluate logic gates\n`;
    
    if(collapsed_nodes && collapsed_nodes.length > 0) {
        // Sort by depth (topological sort)
        collapsed_nodes.sort((a,b) => (a.depth || 0) - (b.depth || 0));
       
        collapsed_nodes.forEach(gn => {
            if (gn.type === "INPUT") return;
            
            cpp += `  // processing ${gn.type} gate at ${gn.id}\n`;
            
            let upstreams = [];
            if (gn.sources) {
                gn.sources.forEach(srcId => {
                    let fromGate = collapsed_nodes.find(g => g.id === srcId || g.originalId === srcId);
                    if (fromGate) {
                         upstreams.push(`var_${fromGate.id.replace(/[^a-zA-Z0-9]/g, "_")}`);
                    }
                });
            }
            
            let safeId = `${gn.id.replace(/[^a-zA-Z0-9]/g, "_")}`;
            let argA = upstreams.length > 0 ? upstreams[0] : "false";
            let argB = upstreams.length > 1 ? upstreams[1] : "false";
             
            if(gn.type === "AND") {
                cpp += `  bool var_${safeId} = ${argA} && ${argB};\n`;
            } else if (gn.type === "NAND") {
                cpp += `  bool var_${safeId} = !(${argA} && ${argB});\n`;
            } else if (gn.type === "NOT") {
                cpp += `  bool var_${safeId} = !${argA};\n`;
            } else if (gn.type === "OR") {
                cpp += `  bool var_${safeId} = ${argA} || ${argB};\n`;
            } else if (gn.type === "XOR") {
                cpp += `  bool var_${safeId} = ${argA} ^ ${argB};\n`;
            } else if (gn.type === "DELAY") {
                cpp += `  bool var_${safeId} = ${argA};\n  delay(${gn.delay_ms}); // buffer delay\n`;
            } else if (gn.type === "SR_LATCH") {
                cpp += `  static bool var_${safeId} = false; // persistent Latch state\n`;
                cpp += `  if (${argA}) var_${safeId} = true; // Set\n`;
                cpp += `  else if (${argB}) var_${safeId} = false; // Reset\n`;
            } else {
                cpp += `  bool var_${safeId} = ${argA};\n`;
            }
        });

        cpp += `\n  // final output write (e.g., LEDs)\n`;
        let outputPin = 8;
        collapsed_nodes.forEach((out_node) => {
            if (out_node.type !== "INPUT" && out_node.type !== "DELAY") {
                cpp += `  digitalWrite(${outputPin++}, var_${out_node.id.replace(/[^a-zA-Z0-9]/g, "_")});\n`;
            }
        });
    }
   
    cpp += `}\n`;
    document.getElementById("cpp_out").innerText = cpp;
}
