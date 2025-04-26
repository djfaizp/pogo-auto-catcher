/*
 * Frida Detection Bypass Script
 * 
 * This script implements various techniques to bypass Frida detection in Pokémon GO.
 * It hooks common detection methods and modifies their behavior to prevent detection.
 */

import "frida-il2cpp-bridge";

// Debug logging function
function logDebug(component, message, data = null) {
    const timestamp = new Date().toISOString();
    const dataStr = data ? JSON.stringify(data) : "";
    console.log(`[DEBUG][${timestamp}][FRIDA_BYPASS][${component}] ${message} ${dataStr}`);
}

// Error logging function
function logError(component, message, error = null) {
    const timestamp = new Date().toISOString();
    const errorStr = error ? `\nError: ${error}\nStack: ${error.stack || "No stack trace"}` : "";
    console.log(`[ERROR][${timestamp}][FRIDA_BYPASS][${component}] ${message}${errorStr}`);
}

// Initialize
console.log("[+] Frida detection bypass script loaded");
logDebug("INIT", "Frida detection bypass initialized");

// Main bypass implementation
function implementBypass() {
    try {
        logDebug("BYPASS", "Starting Frida detection bypass implementation");

        // ==================== BYPASS COMMON DETECTION METHODS ====================

        // 1. Bypass file-based detection (checking for Frida-related files)
        bypassFileDetection();

        // 2. Bypass process name detection
        bypassProcessDetection();

        // 3. Bypass port scanning detection (Frida uses port 27042 by default)
        bypassPortDetection();

        // 4. Bypass memory pattern detection
        bypassMemoryPatternDetection();

        // 5. Bypass native library detection
        bypassNativeLibraryDetection();

        // 6. Bypass Java/JNI-based detection
        bypassJavaDetection();

        logDebug("BYPASS", "Frida detection bypass implementation completed");
    } catch (error) {
        logError("BYPASS", "Error implementing Frida detection bypass", error);
    }
}

// Bypass file-based detection methods
function bypassFileDetection() {
    try {
        logDebug("FILE_BYPASS", "Implementing file-based detection bypass");

        // Hook file access functions in libc
        const libc = Process.getModuleByName("libc.so");

        // Hook open() function
        Interceptor.attach(libc.getExportByName("open"), {
            onEnter: function(args) {
                const path = args[0].readUtf8String();
                this.path = path;

                // Check if the path contains Frida-related strings
                if (path && (
                    path.includes("frida") || 
                    path.includes("gadget") || 
                    path.includes("/proc/self/maps") || 
                    path.includes("/proc/self/status") ||
                    path.includes("/proc/self/task") ||
                    path.includes("/proc/self/mem")
                )) {
                    logDebug("FILE_BYPASS", `Detected attempt to open Frida-related file: ${path}`);
                    this.shouldModify = true;
                }
            },
            onLeave: function(retval) {
                // If it's a Frida-related file, return "file not found" error
                if (this.shouldModify) {
                    logDebug("FILE_BYPASS", `Modifying return value for file: ${this.path}`);
                    retval.replace(-1); // ENOENT (No such file or directory)
                }
            }
        });

        // Hook stat() function
        Interceptor.attach(libc.getExportByName("stat"), {
            onEnter: function(args) {
                const path = args[0].readUtf8String();
                this.path = path;

                // Check if the path contains Frida-related strings
                if (path && (
                    path.includes("frida") || 
                    path.includes("gadget") || 
                    path.includes("/proc/self/maps") || 
                    path.includes("/proc/self/status")
                )) {
                    logDebug("FILE_BYPASS", `Detected attempt to stat Frida-related file: ${path}`);
                    this.shouldModify = true;
                }
            },
            onLeave: function(retval) {
                // If it's a Frida-related file, return "file not found" error
                if (this.shouldModify) {
                    logDebug("FILE_BYPASS", `Modifying stat return value for file: ${this.path}`);
                    retval.replace(-1); // ENOENT (No such file or directory)
                }
            }
        });

        // Hook fopen() function
        Interceptor.attach(libc.getExportByName("fopen"), {
            onEnter: function(args) {
                const path = args[0].readUtf8String();
                this.path = path;

                // Check if the path contains Frida-related strings
                if (path && (
                    path.includes("frida") || 
                    path.includes("gadget") || 
                    path.includes("/proc/self/maps") || 
                    path.includes("/proc/self/status")
                )) {
                    logDebug("FILE_BYPASS", `Detected attempt to fopen Frida-related file: ${path}`);
                    this.shouldModify = true;
                }
            },
            onLeave: function(retval) {
                // If it's a Frida-related file, return NULL (file not found)
                if (this.shouldModify) {
                    logDebug("FILE_BYPASS", `Modifying fopen return value for file: ${this.path}`);
                    retval.replace(ptr(0)); // NULL pointer
                }
            }
        });

        logDebug("FILE_BYPASS", "File-based detection bypass implemented");
    } catch (error) {
        logError("FILE_BYPASS", "Error implementing file-based detection bypass", error);
    }
}

// Bypass process name detection
function bypassProcessDetection() {
    try {
        logDebug("PROCESS_BYPASS", "Implementing process detection bypass");

        // Hook functions that might be used to detect Frida processes
        const libc = Process.getModuleByName("libc.so");

        // Hook readdir() function to hide Frida-related processes
        Interceptor.attach(libc.getExportByName("readdir"), {
            onLeave: function(retval) {
                if (retval.isNull()) {
                    return;
                }

                // dirent structure has d_name at offset 19 on 64-bit Android
                const d_name = retval.add(19).readUtf8String();
                
                // Check if the directory entry is Frida-related
                if (d_name && (
                    d_name.includes("frida") || 
                    d_name.includes("gadget") || 
                    d_name.includes("gum-js")
                )) {
                    logDebug("PROCESS_BYPASS", `Hiding Frida-related process: ${d_name}`);
                    // Call readdir() again to get the next entry
                    const next = this.readdir();
                    retval.replace(next);
                }
            }
        });

        // Hook getpid() to prevent detection of our own process
        const originalGetpid = new NativeFunction(libc.getExportByName("getpid"), "int", []);
        Interceptor.replace(libc.getExportByName("getpid"), new NativeCallback(function() {
            const pid = originalGetpid();
            logDebug("PROCESS_BYPASS", `getpid() called, returning: ${pid}`);
            return pid;
        }, "int", []));

        logDebug("PROCESS_BYPASS", "Process detection bypass implemented");
    } catch (error) {
        logError("PROCESS_BYPASS", "Error implementing process detection bypass", error);
    }
}

// Bypass port scanning detection
function bypassPortDetection() {
    try {
        logDebug("PORT_BYPASS", "Implementing port scanning detection bypass");

        // Hook socket-related functions to prevent detection of Frida's default port (27042)
        const libc = Process.getModuleByName("libc.so");

        // Hook connect() function
        Interceptor.attach(libc.getExportByName("connect"), {
            onEnter: function(args) {
                const sockfd = args[0].toInt32();
                const sockaddr = args[1];
                
                // Check if it's an IPv4 or IPv6 address (AF_INET = 2, AF_INET6 = 10)
                const sa_family = sockaddr.add(0).readU16();
                
                if (sa_family === 2) { // IPv4
                    // sockaddr_in structure: { sa_family, sin_port, sin_addr, ... }
                    const port = sockaddr.add(2).readU16();
                    
                    // Check if connecting to Frida's default port
                    if (port === 27042 || port === 27043) {
                        logDebug("PORT_BYPASS", `Detected connection attempt to Frida port: ${port}`);
                        this.shouldModify = true;
                    }
                } else if (sa_family === 10) { // IPv6
                    // sockaddr_in6 structure: { sa_family, sin6_port, ... }
                    const port = sockaddr.add(2).readU16();
                    
                    // Check if connecting to Frida's default port
                    if (port === 27042 || port === 27043) {
                        logDebug("PORT_BYPASS", `Detected IPv6 connection attempt to Frida port: ${port}`);
                        this.shouldModify = true;
                    }
                }
            },
            onLeave: function(retval) {
                // If it's a connection to Frida's port, make it fail
                if (this.shouldModify) {
                    logDebug("PORT_BYPASS", "Modifying connect() return value to indicate failure");
                    retval.replace(-1); // Connection failed
                }
            }
        });

        logDebug("PORT_BYPASS", "Port scanning detection bypass implemented");
    } catch (error) {
        logError("PORT_BYPASS", "Error implementing port scanning detection bypass", error);
    }
}

// Bypass memory pattern detection
function bypassMemoryPatternDetection() {
    try {
        logDebug("MEMORY_BYPASS", "Implementing memory pattern detection bypass");

        // Hook memory scanning functions
        const libc = Process.getModuleByName("libc.so");

        // Hook mmap() to prevent memory scanning
        Interceptor.attach(libc.getExportByName("mmap"), {
            onEnter: function(args) {
                // Check if it's trying to map /proc/self/maps or similar
                const prot = args[2].toInt32();
                const flags = args[3].toInt32();
                const fd = args[4].toInt32();
                
                // Store the file descriptor for later use
                this.fd = fd;
            },
            onLeave: function(retval) {
                // If it's a suspicious memory mapping, we could modify it
                // but this is complex and might cause stability issues
                // For now, we just log it
                if (this.fd > 0) {
                    logDebug("MEMORY_BYPASS", `Memory mapping from fd ${this.fd} at address ${retval}`);
                }
            }
        });

        // Hook memcmp() to prevent pattern matching
        const originalMemcmp = new NativeFunction(libc.getExportByName("memcmp"), "int", ["pointer", "pointer", "size_t"]);
        Interceptor.replace(libc.getExportByName("memcmp"), new NativeCallback(function(s1, s2, n) {
            // Check if one of the buffers contains Frida signatures
            let containsFridaPattern = false;
            
            // Only check small buffers to avoid performance impact
            if (n < 100) {
                try {
                    const buf1 = s1.readByteArray(n);
                    const buf2 = s2.readByteArray(n);
                    
                    // Convert to strings for simple pattern matching
                    const str1 = buf1.toString();
                    const str2 = buf2.toString();
                    
                    // Check for Frida-related strings
                    if ((str1 && (str1.includes("frida") || str1.includes("gum"))) ||
                        (str2 && (str2.includes("frida") || str2.includes("gum")))) {
                        containsFridaPattern = true;
                        logDebug("MEMORY_BYPASS", "Detected Frida pattern in memcmp()");
                    }
                } catch (e) {
                    // Ignore errors reading memory
                }
            }
            
            // If Frida pattern detected, return non-match
            if (containsFridaPattern) {
                return 1; // Indicate buffers don't match
            }
            
            // Otherwise, call the original function
            return originalMemcmp(s1, s2, n);
        }, "int", ["pointer", "pointer", "size_t"]));

        logDebug("MEMORY_BYPASS", "Memory pattern detection bypass implemented");
    } catch (error) {
        logError("MEMORY_BYPASS", "Error implementing memory pattern detection bypass", error);
    }
}

// Bypass native library detection
function bypassNativeLibraryDetection() {
    try {
        logDebug("LIBRARY_BYPASS", "Implementing native library detection bypass");

        // Hook dlopen() to prevent detection of Frida libraries
        const libc = Process.getModuleByName("libc.so");
        
        Interceptor.attach(libc.getExportByName("dlopen"), {
            onEnter: function(args) {
                const path = args[0].readUtf8String();
                this.path = path;
                
                // Check if it's trying to load a Frida-related library
                if (path && (
                    path.includes("frida") || 
                    path.includes("gadget") || 
                    path.includes("gum")
                )) {
                    logDebug("LIBRARY_BYPASS", `Detected attempt to dlopen Frida library: ${path}`);
                    this.shouldModify = true;
                }
            },
            onLeave: function(retval) {
                // If it's a Frida library, return NULL (library not found)
                if (this.shouldModify) {
                    logDebug("LIBRARY_BYPASS", `Modifying dlopen return value for library: ${this.path}`);
                    retval.replace(ptr(0)); // NULL pointer
                }
            }
        });

        logDebug("LIBRARY_BYPASS", "Native library detection bypass implemented");
    } catch (error) {
        logError("LIBRARY_BYPASS", "Error implementing native library detection bypass", error);
    }
}

// Bypass Java/JNI-based detection
function bypassJavaDetection() {
    try {
        logDebug("JAVA_BYPASS", "Implementing Java-based detection bypass");

        // Use Java.perform to hook Java methods
        Java.perform(function() {
            try {
                // Hook common Java-based detection methods
                
                // 1. Hook Runtime.exec() to prevent command execution for detection
                const Runtime = Java.use("java.lang.Runtime");
                Runtime.exec.overload("java.lang.String").implementation = function(cmd) {
                    // Check if the command is trying to detect Frida
                    if (cmd && (
                        cmd.includes("frida") || 
                        cmd.includes("ps") || 
                        cmd.includes("netstat") || 
                        cmd.includes("lsof") ||
                        cmd.includes("grep")
                    )) {
                        logDebug("JAVA_BYPASS", `Detected Runtime.exec command for Frida detection: ${cmd}`);
                        
                        // Return an empty process
                        const ProcessImpl = Java.use("java.lang.ProcessImpl");
                        return ProcessImpl.$new();
                    }
                    
                    // Otherwise, call the original method
                    return this.exec(cmd);
                };
                
                // Hook the other overloads of exec()
                Runtime.exec.overload("[Ljava.lang.String;").implementation = function(cmdArray) {
                    if (cmdArray && cmdArray.length > 0) {
                        const cmd = cmdArray[0];
                        if (cmd && (
                            cmd.includes("frida") || 
                            cmd.includes("ps") || 
                            cmd.includes("netstat") || 
                            cmd.includes("lsof") ||
                            cmd.includes("grep")
                        )) {
                            logDebug("JAVA_BYPASS", `Detected Runtime.exec command array for Frida detection: ${cmd}`);
                            
                            // Return an empty process
                            const ProcessImpl = Java.use("java.lang.ProcessImpl");
                            return ProcessImpl.$new();
                        }
                    }
                    
                    return this.exec(cmdArray);
                };
                
                // 2. Hook System.getProperty() to hide Frida-related properties
                const System = Java.use("java.lang.System");
                System.getProperty.overload("java.lang.String").implementation = function(name) {
                    // Check if querying for Frida-related properties
                    if (name && (
                        name.includes("frida") || 
                        name.includes("gadget") || 
                        name.includes("wrapped")
                    )) {
                        logDebug("JAVA_BYPASS", `Detected System.getProperty for Frida detection: ${name}`);
                        return null;
                    }
                    
                    return this.getProperty(name);
                };
                
                // 3. Hook File operations that might be used for detection
                const File = Java.use("java.io.File");
                
                // Hook exists() to hide Frida-related files
                File.exists.implementation = function() {
                    const fileName = this.getAbsolutePath();
                    
                    if (fileName && (
                        fileName.includes("frida") || 
                        fileName.includes("gadget") || 
                        fileName.includes("/proc/self/maps") || 
                        fileName.includes("/proc/self/status")
                    )) {
                        logDebug("JAVA_BYPASS", `Detected File.exists check for Frida-related file: ${fileName}`);
                        return false;
                    }
                    
                    return this.exists();
                };
                
                // 4. Hook Socket operations to prevent port scanning
                const Socket = Java.use("java.net.Socket");
                
                // Hook connect() to prevent connecting to Frida ports
                Socket.connect.overload("java.net.SocketAddress", "int").implementation = function(address, timeout) {
                    try {
                        // Try to extract the port from the SocketAddress
                        const addressStr = address.toString();
                        
                        // Check if connecting to Frida's default port
                        if (addressStr && (
                            addressStr.includes(":27042") || 
                            addressStr.includes(":27043")
                        )) {
                            logDebug("JAVA_BYPASS", `Detected Socket.connect to Frida port: ${addressStr}`);
                            throw new Java.use("java.net.ConnectException").$new("Connection refused");
                        }
                    } catch (e) {
                        // Ignore errors in our detection code
                    }
                    
                    // Call the original method
                    return this.connect(address, timeout);
                };
                
                logDebug("JAVA_BYPASS", "Java-based detection bypass implemented");
            } catch (error) {
                logError("JAVA_BYPASS", "Error in Java detection bypass", error);
            }
        });
    } catch (error) {
        logError("JAVA_BYPASS", "Error implementing Java-based detection bypass", error);
    }
}

// Execute the bypass implementation
implementBypass();

// Export the bypass functions for use in other scripts
module.exports = {
    bypassFileDetection,
    bypassProcessDetection,
    bypassPortDetection,
    bypassMemoryPatternDetection,
    bypassNativeLibraryDetection,
    bypassJavaDetection
};
