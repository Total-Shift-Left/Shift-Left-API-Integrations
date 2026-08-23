'use strict';

/**
 * Rasterizes icon-source/total-shift-left-icon.svg to PNGs for Azure DevOps / VS Marketplace.
 * (SVG must not live under packaged `images/` — Marketplace rejects SVG in the VSIX.)
 * Run from shiftleft-api-integration-task: npm run generate-extension-icon
 */
const path = require('path');
const sharp = require('sharp');

const extRoot = path.resolve(__dirname, '../..');
const taskRoot = path.resolve(__dirname, '..');
const svgPath = path.join(extRoot, 'icon-source', 'total-shift-left-icon.svg');
const marketplacePng = path.join(extRoot, 'images', 'extension-icon.png');
const taskIconPng = path.join(taskRoot, 'icon.png');

const rasterOpts = {
  fit: 'contain',
  background: { r: 255, g: 255, b: 255, alpha: 0 },
};

async function main() {
  await sharp(svgPath)
    .resize(128, 128, rasterOpts)
    .png()
    .toFile(marketplacePng);
  console.log('Wrote', marketplacePng);

  // 32×32 for the task catalog (same directory as task.json)
  await sharp(svgPath)
    .resize(32, 32, rasterOpts)
    .png()
    .toFile(taskIconPng);
  console.log('Wrote', taskIconPng);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
