# MochiPic PC receiver: receives photos/videos sent from the MochiPic phone app over Wi-Fi.
# Run via Wi-Fi-receiver .bat (no install needed). Stop with Ctrl+C or by closing the window.
param([string]$Root = "")

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrEmpty($Root)) {
    $cfg = Join-Path $here "receiver_root.txt"
    if (Test-Path $cfg) { $Root = (Get-Content $cfg -Encoding UTF8 | Select-Object -First 1).Trim() }
}
if ([string]::IsNullOrEmpty($Root)) { $Root = Join-Path ([Environment]::GetFolderPath("MyPictures")) "MochiPic" }
if (-not (Test-Path $Root)) { New-Item -ItemType Directory -Path $Root -Force | Out-Null }

$src = @"
using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

public class MochiReceiver
{
    public const int Port = 48765;
    static string root;
    static object logLock = new object();

    public static void Log(string s)
    {
        lock (logLock) { Console.WriteLine(DateTime.Now.ToString("HH:mm:ss") + "  " + s); }
    }

    public static void Run(string rootDir)
    {
        root = Path.GetFullPath(rootDir);
        Thread udp = new Thread(UdpLoop);
        udp.IsBackground = true;
        udp.Start();
        TcpListener l = new TcpListener(IPAddress.Any, Port);
        l.Start();
        while (true)
        {
            TcpClient c = l.AcceptTcpClient();
            Thread t = new Thread(Handle);
            t.IsBackground = true;
            t.Start(c);
        }
    }

    static void UdpLoop()
    {
        try
        {
            UdpClient u = new UdpClient(Port);
            while (true)
            {
                IPEndPoint ep = new IPEndPoint(IPAddress.Any, 0);
                byte[] data = u.Receive(ref ep);
                string msg = Encoding.UTF8.GetString(data);
                if (msg.StartsWith("MOCHIPIC?"))
                {
                    byte[] reply = Encoding.UTF8.GetBytes("MOCHIPIC|" + Environment.MachineName + "|" + Port);
                    u.Send(reply, reply.Length, ep);
                }
            }
        }
        catch (Exception e) { Log("UDP stopped: " + e.Message); }
    }

    static string ReadLine(Stream s)
    {
        StringBuilder sb = new StringBuilder();
        List<byte> buf = new List<byte>();
        while (true)
        {
            int b = s.ReadByte();
            if (b < 0) return buf.Count == 0 ? null : Encoding.UTF8.GetString(buf.ToArray());
            if (b == 10) break;
            if (b != 13) buf.Add((byte)b);
            if (buf.Count > 16384) throw new IOException("header too long");
        }
        return Encoding.UTF8.GetString(buf.ToArray());
    }

    static void Reply(Stream s, int code, string text)
    {
        byte[] body = Encoding.UTF8.GetBytes(text);
        string head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + body.Length + "\r\nConnection: close\r\n\r\n";
        byte[] h = Encoding.ASCII.GetBytes(head);
        s.Write(h, 0, h.Length);
        s.Write(body, 0, body.Length);
        s.Flush();
    }

    static string SafeName(string n)
    {
        if (n == null) return "";
        n = n.Replace('/', '_').Replace('\\', '_');
        foreach (char ch in Path.GetInvalidFileNameChars()) n = n.Replace(ch, '_');
        n = n.Trim().TrimEnd('.');
        if (n == "." || n == "..") return "";
        return n;
    }

    static Dictionary<string, string> ParseQuery(string q)
    {
        Dictionary<string, string> d = new Dictionary<string, string>();
        if (q == null) return d;
        foreach (string part in q.Split('&'))
        {
            int i = part.IndexOf('=');
            if (i < 0) continue;
            d[part.Substring(0, i)] = Uri.UnescapeDataString(part.Substring(i + 1).Replace("+", "%20"));
        }
        return d;
    }

    static void Handle(object o)
    {
        TcpClient c = (TcpClient)o;
        string who = "?";
        try
        {
            who = ((IPEndPoint)c.Client.RemoteEndPoint).Address.ToString();
            c.ReceiveTimeout = 60000;
            c.SendTimeout = 60000;
            NetworkStream s = c.GetStream();
            string req = ReadLine(s);
            if (req == null) return;
            string[] rp = req.Split(' ');
            long len = 0;
            while (true)
            {
                string h = ReadLine(s);
                if (h == null || h.Length == 0) break;
                if (h.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase))
                    len = long.Parse(h.Substring(15).Trim());
            }
            string method = rp[0];
            string target = rp.Length > 1 ? rp[1] : "/";
            string path = target;
            string query = null;
            int qi = target.IndexOf('?');
            if (qi >= 0) { path = target.Substring(0, qi); query = target.Substring(qi + 1); }
            Dictionary<string, string> q = ParseQuery(query);

            if (method == "GET" && path == "/ping")
            {
                Reply(s, 200, "MOCHIPIC|" + Environment.MachineName + "|" + Port);
            }
            else if (method == "GET" && path == "/dirs")
            {
                StringBuilder sb = new StringBuilder();
                foreach (string d in Directory.GetDirectories(root))
                    sb.Append(Path.GetFileName(d)).Append("\n");
                Reply(s, 200, sb.ToString());
            }
            else if (method == "PUT" && path == "/file")
            {
                string dir = SafeName(q.ContainsKey("dir") ? q["dir"] : "");
                string name = SafeName(q.ContainsKey("name") ? q["name"] : "");
                if (name.Length == 0) { Reply(s, 400, "bad name"); return; }
                string folder = dir.Length == 0 ? root : Path.Combine(root, dir);
                Directory.CreateDirectory(folder);
                string dest = Path.Combine(folder, name);
                string stem = Path.GetFileNameWithoutExtension(name);
                string ext = Path.GetExtension(name);
                int n = 1;
                while (File.Exists(dest)) { dest = Path.Combine(folder, stem + " (" + n + ")" + ext); n++; }
                string part = dest + ".part";
                long got = 0;
                using (FileStream fs = new FileStream(part, FileMode.Create, FileAccess.Write))
                {
                    byte[] buf = new byte[262144];
                    while (got < len)
                    {
                        int want = (int)Math.Min(buf.Length, len - got);
                        int r = s.Read(buf, 0, want);
                        if (r <= 0) break;
                        fs.Write(buf, 0, r);
                        got += r;
                    }
                }
                if (got != len)
                {
                    try { File.Delete(part); } catch (Exception) { }
                    Log("incomplete: " + name + " (" + got + "/" + len + ") from " + who);
                    return;
                }
                File.Move(part, dest);
                string mt;
                long ms;
                if (q.TryGetValue("mtime", out mt) && long.TryParse(mt, out ms) && ms > 0)
                {
                    try { File.SetLastWriteTime(dest, new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc).AddMilliseconds(ms).ToLocalTime()); } catch (Exception) { }
                }
                Log("received: " + (dir.Length == 0 ? "" : dir + "\\") + Path.GetFileName(dest) + "  (" + (len / 1024) + " KB) from " + who);
                Reply(s, 200, "OK");
            }
            else
            {
                Reply(s, 404, "not found");
            }
        }
        catch (Exception e)
        {
            Log("error from " + who + ": " + e.Message);
        }
        finally
        {
            try { c.Close(); } catch (Exception) { }
        }
    }
}
"@

Add-Type -TypeDefinition $src -Language CSharp

Write-Host "=== MochiPic PC receiver ==="
Write-Host ("Save folder : " + $Root)
Write-Host "PC address  :"
Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } | ForEach-Object { Write-Host ("   " + $_.IPAddress + "  (port 48765)") }
Write-Host ""
Write-Host "Waiting for the phone. Close this window or press Ctrl+C to stop."
Write-Host ""
[MochiReceiver]::Run($Root)
