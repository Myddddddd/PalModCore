// Minimal Minecraft RCON client for the MCMCP dedicated server.
// Usage: node rcon.mjs "<command>" ["<command2>" ...]
import net from 'node:net';

const HOST = '127.0.0.1';
const PORT = 25575;
const PASS = 'mcmcp';

const TYPE_AUTH = 3;
const TYPE_EXEC = 2;

function makePacket(id, type, body) {
  const bodyBuf = Buffer.from(body, 'utf8');
  const len = 4 + 4 + bodyBuf.length + 2;
  const buf = Buffer.alloc(4 + len);
  buf.writeInt32LE(len, 0);
  buf.writeInt32LE(id, 4);
  buf.writeInt32LE(type, 8);
  bodyBuf.copy(buf, 12);
  buf.writeInt8(0, 12 + bodyBuf.length);
  buf.writeInt8(0, 12 + bodyBuf.length + 1);
  return buf;
}

const commands = process.argv.slice(2);
if (commands.length === 0) {
  console.error('No command given');
  process.exit(1);
}

const sock = net.connect(PORT, HOST);
let buffer = Buffer.alloc(0);
let authed = false;
let cmdIndex = 0;
const REQ_ID = 100;

sock.on('connect', () => {
  sock.write(makePacket(1, TYPE_AUTH, PASS));
});

function sendNextCommand() {
  if (cmdIndex >= commands.length) {
    sock.end();
    return;
  }
  sock.write(makePacket(REQ_ID + cmdIndex, TYPE_EXEC, commands[cmdIndex]));
}

sock.on('data', (data) => {
  buffer = Buffer.concat([buffer, data]);
  while (buffer.length >= 4) {
    const len = buffer.readInt32LE(0);
    if (buffer.length < 4 + len) break;
    const id = buffer.readInt32LE(4);
    const body = buffer.toString('utf8', 12, 4 + len - 2);
    buffer = buffer.subarray(4 + len);

    if (!authed) {
      if (id === -1) { console.error('AUTH FAILED'); sock.end(); process.exit(2); }
      authed = true;
      sendNextCommand();
    } else {
      console.log(`>>> ${commands[cmdIndex]}`);
      console.log(body.trim() || '(empty response)');
      console.log('---');
      cmdIndex++;
      sendNextCommand();
    }
  }
});

sock.on('error', (e) => { console.error('RCON error:', e.message); process.exit(3); });
sock.on('close', () => process.exit(0));
