export const APP_VERSION='2.1.4';
export const RELEASES_URL='https://github.com/Devilzhu1985/hk-scout/releases';
export function newerVersion(tag,current=APP_VERSION){
  const parse=s=>/^v?(\d+)\.(\d+)\.(\d+)(?:-preview\.(\d+))?$/.exec(s);
  const a=parse(tag),b=parse(current);if(!a||!b)return false;
  for(let i=1;i<=3;i++){if(Number(a[i])!==Number(b[i]))return Number(a[i])>Number(b[i]);}
  // App versions omit the preview suffix; published previews increment versionCode
  // and the three-part version, so a same-version preview is never an upgrade.
  return false;
}
export function chooseUpdate(releases){
  if(!Array.isArray(releases))throw Error('Could not read the update list.');
  const choices=releases.filter(r=>!r.draft&&newerVersion(r.tag_name)).sort((a,b)=>newerVersion(a.tag_name,b.tag_name)?-1:1);
  for(const release of choices){
    const asset=release.assets?.find(a=>/^scout-[\w.-]+\.apk$/.test(a.name));
    if(!asset||!/^https:\/\/github\.com\/Devilzhu1985\/hk-scout\/releases\/download\/[\w.-]+\/scout-[\w.-]+\.apk$/.test(asset.browser_download_url)||!/^sha256:[a-f0-9]{64}$/.test(asset.digest||''))continue;
    return {version:release.tag_name,url:asset.browser_download_url,sha256:asset.digest.slice(7),preview:!!release.prerelease,size:asset.size};
  }
  return null;
}
export async function checkUpdate(){
  const response=await fetch('https://api.github.com/repos/Devilzhu1985/hk-scout/releases?per_page=30',{headers:{Accept:'application/vnd.github+json'},cache:'no-store',signal:AbortSignal.timeout(15000)});
  if(!response.ok)throw Error('Update check failed. Try again online or open GitHub releases.');
  return chooseUpdate(await response.json());
}
