const fs = require('fs');
const path = require('path');
const JavaScriptObfuscator = require('javascript-obfuscator');

const isDebug = process.argv.includes('--debug');
const inputDir = path.resolve(__dirname, 'public');
const outputDir = path.resolve(__dirname, '../src/main/assets/');
const obfuscateJsFiles = ['requests.js','main.js']

const obfuscateOptions = {
    compact: true,
    controlFlowFlattening: !isDebug,
    controlFlowFlatteningThreshold: 1.0,
    deadCodeInjection: !isDebug,
    deadCodeInjectionThreshold: 1.0,
    disableConsoleOutput: !isDebug,
    stringArray: true,
    stringArrayThreshold: 1.0,
    transformObjectKeys: true,
    unicodeEscapeSequence: true,
    renameGlobals: false,
};

if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

function copyOrObfuscateFile(entryPath, outPath) {
    const sourceCode = fs.readFileSync(entryPath, 'utf8');
    if (isDebug) {
        fs.writeFileSync(outPath, sourceCode, 'utf8');
        console.log(`🔄 Copied (debug): ${entryPath} -> ${outPath}`);
    } else {
        const obfuscatedCode = JavaScriptObfuscator.obfuscate(sourceCode, obfuscateOptions).getObfuscatedCode();
        fs.writeFileSync(outPath, obfuscatedCode, 'utf8');
        console.log(`✔️ Obfuscated: ${entryPath} -> ${outPath}`);
    }
}

// Recursively process directory
function processDirectory(dir, outDir) {
    const entries = fs.readdirSync(dir);

    entries.forEach((entry) => {
        const entryPath = path.join(dir, entry);
        const outPath = path.join(outDir, entry);
        const stat = fs.statSync(entryPath);

        if (stat.isDirectory()) {
            fs.mkdirSync(outPath, { recursive: true });
            processDirectory(entryPath, outPath);
        } else if (stat.isFile()) {
            if (entry.endsWith('.js') && obfuscateJsFiles.includes(entry)) {
                copyOrObfuscateFile(entryPath, outPath);
            } else {
                // Copy non-JS files directly
                fs.copyFileSync(entryPath, outPath);
                console.log(`📄 Copied (no obfuscation): ${entryPath} -> ${outPath}`);
            }
        }
    });
}

if (isDebug) {
    console.log('[DEBUG] Debug mode enabled: files will be copied as-is (no obfuscation).');
}

processDirectory(inputDir, outputDir);
console.log('\n✅ All files processed.');