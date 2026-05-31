// Web app ingestion and isomorphic matching

let graph_nodes = null;
let collapsed_nodes = [];

// Sample DAG export from the Fabric Mod (simulate ingest)
const sampleJSON = {
    nodes: [
        { id: "n1", type: "NOT_GATE", pos: "[10, 64, 10]" },
        { id: "n2", type: "NOT_GATE", pos: "[12, 64, 10]" },
        { id: "n3", type: "NOT_GATE", pos: "[11, 64, 11]" }
    ],
    edges: [
        { from: "n1", to: "n3" },
        { from: "n2", to: "n3" }
    ]
};











function detectSubGraphs(dag) {
    console.log("Running aggressive Sub-Graph Isomorphism...");
    
    // Check for AND, OR, NOT, NAND, NOR, and SR Latches
    // Naive sub-graph matching pattern:
    // If two NOT gates output to a third NOT gate => AND Gate (or NAND depending on redstone line inversion)
    
    collapsed_nodes = [];
    
  
  // 1 redstone tick = 100ms
  let buffers = dag.nodes.filter(n => n.type === "BUFFER");
  buffers.forEach((b, idk) => {
  let delay = (parseInt(b.delay) || 1) * 100;
  console.log(`calculated delay $(delay_ms) ms for buffer ${b.id}`);
  collapsed_nodes.push({ id: b.id, type: "DELAY", delay_ms: delay_ms, x: 100, y: 150 + (idk * 50)});
    });
  
  





  
  
    let noteGates = dag.nodes.filter(n.type === "NOT_GATE");
   console.log(`Found ${notGates.length} potenial NOT gates`);
   
   
   
   
   let and_counter = 0;
   let or_counter = 0;
   
   
   if(dag.nodes.some(n => n.type === "COMPRATOR" && dag.edges.length > 2)) {
   collapsed_nodes.push({ id: "g_" + and_counter++, type: "AND", x: 150, y: 50});
   console.log("pattern matched: AND");
   }
   
   
   let targetCounts = {};
   dag.edges.forEach(e => {
    targetCounts[e.to] = (targetCounts[e.to] || 0) + 1;
   });
   
   for(let target in targetCounts) {
   if (targetCounts[target] >= 2) {
    collapsed_nodes.push({ id: "g_" + or_counter++, type: "OR", x: 200, y: 100});
   console.log("pattern matched: OR");
   }
}
   
  
let not_counter = 0;


 notGates.forEach(ng => {

   let isIsolated = !dag.edges.some(e => e.to === ng.id && targetCounts[ng.id] > 1 );
    if (isIsolated) {
        collapsed_nodes.push({ id: "g_not_" + not_counter++, type: "NOT", x: 50, y: 250});
    console.log("pattern matched: NOT");
    }
 });

//NOR = OR + NOT

if (or_counter > 0 && not_counter > 0) {
    collapsed_nodes.push({ id: "g_nor_1", type: "NOR", x: 250, y: 200});
      console.log("pattern matched: NOR");
}




let comparators_sub = dag.nodes.filter(n => n.type === "COMPRATOR" && n.mode === "subtract");
comparators_sub.forEach((comp, idx) => {
   collapsed_nodes.push({ id: comp.id, type: "XOR", x: 180, y: 220 + (idx * 50) });
 console.log(`pattern matched XOR logic from comparator (subtract) at ${comp.id}`);
});

// Naive mock pattern matching for the NAND/SR logic
    if (dag.nodes.length >= 3) {
        console.log("pattern matched: NAND");
        collapsed_nodes.push({ id: "g1", type: "NAND", x: 100, y: 150 });
    }
    
    // ...other patterns: NOR, SR Latch, OR
    console.log("pattern matched: SR_LATCH");
    collapsed_nodes.push({ id: "g2", type: "SR_LATCH", x: 300, y: 150 });
    
    return collapsed_nodes;
}













function renderSVG(gates) {
    const container = document.getElementById("svg_container");
    let svgContent = `<svg width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">`;
    
    
    let depth_tracker = {};
    
    gates.forEach(gate => {
        // TODO: fix the SVG line routing if i have time before the deadline
       
       
       
    
    
    let in_degree = 0;
     if( graph_nodes && graph_nodes.edges) {
     in_degree = graph_nodes.edges.filter(e => e.to === gate.id).length;
     }
       gate_depth = in_degree;
    
      let leve_x = (gate.depth) * 150 + 50;
        gate.x = level_x;
    
    
      if (!depth_tracker[gate.depth]) { depth_tracker[gate.depth] = 0; }
         gate.y = depth_tracker[gate.depth] * 80 + 50;      
        depth_tracker[gate.depth]++;
    });


    if (graph_nodes &&graph_nodes.edges) {
      graph_nodes.edges.forEach(edge => {
        let fromGate = gates.find(g => g.id === edge.from);
        let toGate = gates.find(g => g.id === edge.to);

       if (fromGate && toGate) {

       let startX = fromGate.x + 50;
       let startY = fromGate.y + 20;
       let endX = toGate.x + 20;
       let endY = toGate.y + 20;


       let ctrlX1 = startX + 40;
    let ctrlX2 = endX - 40;

       svgContent +=
          <path d="M ${startX},${startY} C ${ctrlX1},${startY} ${ctrlX2},${endY} ${endX},${endY}" fill="none" stroke="#e74c3c" stroke-width="2" />
          `;
       }
       });
       }


       gates.forEach(gate => {
        if (gate.type === "NAND") {
            // Draw standard IEEE NAND D-shape + inversion bubble
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <path d="M 0,0 L 20,0 A 20,20 0 0,1 20,40 L 0,40 Z" fill="none" stroke="white" stroke-width="2"/>
                    <circle cx="45" cy="20" r="5" fill="none" stroke="white" stroke-width="2"/>
                    <text x="5" y="25" fill="white" font-size="12">NAND</text>
                </g>
            `;
        }
        else if (gate.type === "SR_LATCH") {
            // Draw SR Latch box
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                    <rect x="0" y="0" width="50" height="60" fill="none" stroke="white" stroke-width="2"/>
                    <text x="5" y="20" fill="white" font-size="12">S</text>
                    <text x="5" y="50" fill="white" font-size="12">R</text>
                    <text x="35" y="20" fill="white" font-size="12">Q</text>
                    <text x="35" y="50" fill="white" font-size="12">Q'</text>
                </g>
            `;
        }
            else if (gate.type === "AND") {
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                 <path d="M 0,0 L 25,0 A 25,20 0 0,1 25,40 L 0,40 Z" fill="none" stroke="white" stroke-width="2"/>
                <text x="5" y="25" fill="white" font-size="12">AND</text>
                </g>
            `;
        }
        else if (gate.type === "OR") {
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
                  <path d="M 0,0 Q 15,20 0,40 Q 25,40 40,20 Q 25,0 0,0" fill="none" stroke="white" stroke-width="2"/>
                    <text x="10" y="25" fill="white" font-size="12">OR</text>
                </g>  
            `;
        }
        else if (gate.type === "NOT") {
            svgContent += `
                <g transform="translate(${gate.x}, ${gate.y})">
               <polygon points="0,0 30,20 0,40" fill="none" stroke="white" stroke-width="2"/>
                    <circle cx="35" cy="20" r="5" fill="none" stroke="white" stroke-width="2"/>
           </g>
            `;
        }
    });
    
    svgContent += `</svg>`;
    container.innerHTML = svgContent;
}

function synthesize() {
    graph_nodes = sampleJSON;
    let logicGates = detectSubGraphs(graph_nodes);
    renderSVG(logicGates);
}




















function generateCPP() {
    console.log("Transpiling DAG to C++ Arudino code...");

    let cpp = `// Auto-generated from Readstone-to-Real\n\n`;
    cpp += `void setup() {\n`;}
    cpp += `  Serial.begin(9600);\n`;


    let pinCounter = 2; 
     let inputArray = {};
    if (graph_nodes && graph_nodes.nodes) {
        let inputs = graph_nodes.nodes.filter(n => n.type === "INPUT");
        inputs.forEach((inp, idx) => {
         let pin = pinCounter + idx;
        cpp += ` pinMode(%${pin}, INPUT_PULLUP);\n`;
            inputArray[inp.id] = pin;   
    });
    }
    
    cpp += `}\n\n`;
    cpp += `void loop() {\n`;


cpp += `  // reading physical switches into memory\n`;
    for (const [id,pin] of Object.entries(inputArrayMap)) {
    let safeId = id.replace(/[^a-zA-Z0-9]/g, '_');
    cpp += ` bool var_${safeId} = digitalRead(${pin}); // active low\n`;
    }
    
    
    
    cpp += `  // evaluate inputs and  outputs via mapped gates\n`;
    cpp += ` bool out = false;\n`;

    if(collapsed_nodes && collapsed_nodes.length > 0) {
       collapsed_nodes.sort(a,b) => (a.depth || 0) - (b.depth || 0));
       
        collapsed_nodes.forEach(gn => {
            cpp += ` // processing ${gn.type} gate at ${gn.id}\n`;
         
         
         let upstream = [];
         if (graph_nodes.edges) {
            graph_nodes.edges.filter(e => e.to === gn.id).forEach(e => {
               upstreams.push(`var_${e.from.replace(/[^a-zA-Z0-9]/g, "_")}`);
            });
        }
         let safeId = `var_${gn.id.replace(/[^a-zA-Z0-9]/g, "_")}`;
         
         
          let argA = upstreams.length > 0 ? upstreams[0] : "false";
         let argB = upstreams.length > 1 ? upstreams[1] : "false";
         
            if(gn.type === "AND") {
             cpp += ` bool ${safeId} = ${argA} && ${argB};\n`;
               } else if (gn.type === "NOT") {
                cpp += ` bool ${safeId} = !${argA};\n`;
                } else if (gn.type === "OR") {
                cpp += ` bool ${safeId} = ${argA} || ${argB};\n`;
                } else if (gn.type === "XOR") {
             cpp += ` bool ${safeId} = ${argA} ^ ${argB};\n`;
            } else if (gn.type === "DELAY") {
                cpp += ` delay(${gn.delay_ms}); // translated buffer tick wait\n`;
                }
        });
    

   cpp += `\n  // final output write\n`;
   let outputs = collapsed_nodes.filter(n => n.type === "DELAY" || n.type === "AND" || n.type === "AND" || n.type === "OR");
   output.forEach((out_node, idx) => {
    cpp += `    digitalWrite(${8 + idx}, ${out_node.id.replace(/[^a-zA-Z0-9]/g, "_")});\n`;
   });
    }
   
    cpp += `}\n`;

    document.getElementById("cpp_out").innerText = cpp;
        }