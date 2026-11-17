// Prepares the generated Android project for JEG.
// Runs after "npx cap add android" (GitHub Actions does this for you).
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const pkgDir = path.join(root, 'android/app/src/main/java/com/adasoft/jeg');
const manifestPath = path.join(root, 'android/app/src/main/AndroidManifest.xml');

// 1. JEG's native code (ears, voice, full screen, keep awake)
fs.mkdirSync(pkgDir, { recursive: true });
for (const f of ['MainActivity.java', 'JegSpeechPlugin.java']) {
  fs.copyFileSync(path.join(root, 'native', f), path.join(pkgDir, f));
  console.log('copied', f);
}

// 2. Permissions, speech services and landscape screen
let m = fs.readFileSync(manifestPath, 'utf8');
const permissions = [
  'android.permission.CAMERA',
  'android.permission.RECORD_AUDIO',
  'android.permission.MODIFY_AUDIO_SETTINGS',
  'android.permission.INTERNET',
];
let add = '';
for (const p of permissions) {
  if (!m.includes(`"${p}"`)) add += `    <uses-permission android:name="${p}" />\n`;
}
if (!m.includes('android.hardware.camera')) {
  add += '    <uses-feature android:name="android.hardware.camera" android:required="false" />\n';
}
if (!m.includes('android.speech.RecognitionService')) {
  add += [
    '    <queries>',
    '        <intent><action android:name="android.speech.RecognitionService" /></intent>',
    '        <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>',
    '    </queries>',
    '',
  ].join('\n');
}
if (add) m = m.replace(/(\s*)<application/, `\n${add}$1<application`);

if (!m.includes('android:screenOrientation')) {
  m = m.replace(/<activity\b/, '<activity\n            android:screenOrientation="sensorLandscape"');
}
fs.writeFileSync(manifestPath, m);
console.log('AndroidManifest.xml patched');
