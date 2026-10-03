import http from 'node:http';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('..',import.meta.url));
const port=Number(process.env.SCOUT_PORT||4173);
const allowed=new Set(['index.html','app.js','styles.css','sw.js','manifest.webmanifest','icon-180.png','icon-192.png','icon-512.png']);
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.webmanifest':'application/manifest+json','.png':'image/png'};
http.createServer(async(req,res)=>{
  try{
    const file=decodeURIComponent(new URL(req.url,'http://localhost').pathname).replace(/^\//,'')||'index.html';
    if(!allowed.has(file)){res.writeHead(404);res.end('Not found');return;}
    res.writeHead(200,{'Content-Type':types[path.extname(file)],'Cache-Control':'no-cache','X-Content-Type-Options':'nosniff'});
    res.end(await readFile(path.join(root,file)));
  }catch{res.writeHead(500);res.end('Unable to read application asset');}
}).listen(port,'127.0.0.1',()=>console.log('Scout preview: http://127.0.0.1:'+port));
