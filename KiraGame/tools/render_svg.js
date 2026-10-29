// Рендер SVG -> PNG через Chromium (для предпросмотра ассетов во время разработки).
// node render_svg.js in.svg out.png [width] [height] [bg]
const {chromium} = require('playwright');
const fs = require('fs');
(async () => {
  const [inp, out, w, h, bg] = process.argv.slice(2);
  const svg = fs.readFileSync(inp, 'utf8');
  const b = await chromium.launch();
  const p = await b.newPage({viewport: {width: +(w||1000), height: +(h||1000)}});
  await p.setContent(`<html><body style="margin:0;background:${bg||'#777'}">${svg.replace(/<svg /,'<svg style="width:100%;height:100%;display:block" ')}</body></html>`);
  await p.screenshot({path: out});
  await b.close();
})();
