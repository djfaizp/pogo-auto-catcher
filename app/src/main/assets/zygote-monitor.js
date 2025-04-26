/*
 * Zygote Monitor Script for Frida
 * 
 * This script monitors Zygote process forking events to detect when Pokémon GO is launched
 * and automatically attaches Frida to it.
 */

// Debug logging function
function logDebug(component, message, data = null) {
    const timestamp = new Date().toISOString();
    const dataStr = data ? JSON.stringify(data) : "";
    console.log(`[DEBUG][${timestamp}][ZYGOTE_MONITOR][${component}] ${message} ${dataStr}`);
}

// Error logging function
function logError(component, message, error = null) {
    const timestamp = new Date().toISOString();
    const errorStr = error ? `\nError: ${error}\nStack: ${error.stack || "No stack trace"}` : "";
    console.log(`[ERROR][${timestamp}][ZYGOTE_MONITOR][${component}] ${message}${errorStr}`);
}

// List of Pokémon GO package names to monitor
const POKEMON_GO_PACKAGES = [
    "com.nianticlabs.pokemongo",           // Standard release
    "com.nianticlabs.pokemongo.beta",      // Beta version
    "com.nianticlabs.pokemongo.uat",       // Testing version
    "com.pokemon.pokemongo",               // Alternative package name
    "com.nianticlabs.pokemongo.obb",       // OBB version
    "com.nianticlabs.pokemongo.dev",       // Development version
    "com.nianticlabs.pokemongo.qa",        // QA version
    "com.pokemongo.nianticlabs",           // Alternative order
    "com.pokemongo.pokemon"                // Another alternative
];

// Path to the Pokémon GO hook script
const POKEMON_GO_HOOK_SCRIPT = "/data/data/com.catcher.pogoauto/files/pokemon-go-hook.js";

// Initialize
console.log("[+] Zygote monitor script loaded");
logDebug("INIT", "Zygote monitor initialized");

// Function to check if a process is Pokémon GO
function isPokemonGoProcess(processName) {
    return POKEMON_GO_PACKAGES.some(pkg => processName.includes(pkg));
}

// Function to attach to Pokémon GO process
function attachToPokemonGo(pid, packageName) {
    logDebug("ATTACH", `Attaching to Pokémon GO process: ${packageName} (PID: ${pid})`);
    
    try {
        // Create a Frida session for the target process
        const session = Frida.attach(pid);
        logDebug("ATTACH", `Successfully attached to process ${pid}`);
        
        // Load the Pokémon GO hook script
        logDebug("SCRIPT", `Loading hook script from ${POKEMON_GO_HOOK_SCRIPT}`);
        
        try {
            const script = session.createScriptFromFile(POKEMON_GO_HOOK_SCRIPT);
            
            // Set up message handler
            script.on('message', function(message) {
                if (message.type === 'send') {
                    logDebug("MESSAGE", `Message from hook script: ${message.payload}`);
                } else if (message.type === 'error') {
                    logError("SCRIPT_ERROR", `Error in hook script: ${message.description}`, new Error(message.stack));
                }
            });
            
            // Load the script
            script.load();
            logDebug("SCRIPT", "Hook script loaded successfully");
            
            return true;
        } catch (scriptError) {
            logError("SCRIPT", `Failed to load hook script: ${scriptError.message}`, scriptError);
            return false;
        }
    } catch (attachError) {
        logError("ATTACH", `Failed to attach to process ${pid}: ${attachError.message}`, attachError);
        return false;
    }
}

// Monitor Zygote for process forking
try {
    logDebug("MONITOR", "Starting Zygote process monitoring");
    
    // Hook the Zygote process
    Interceptor.attach(Module.findExportByName(null, "fork"), {
        onLeave: function(retval) {
            const pid = retval.toInt32();
            
            if (pid > 0) {
                logDebug("FORK", `Process forked with PID: ${pid}`);
                
                // Use setTimeout to give the process time to initialize
                setTimeout(function() {
                    try {
                        // Check if this is a Pokémon GO process
                        const procName = getProcessName(pid);
                        
                        if (procName && isPokemonGoProcess(procName)) {
                            logDebug("DETECT", `Detected Pokémon GO process: ${procName} (PID: ${pid})`);
                            
                            // Attach to the process
                            if (attachToPokemonGo(pid, procName)) {
                                logDebug("SUCCESS", `Successfully attached to Pokémon GO process: ${procName} (PID: ${pid})`);
                            } else {
                                logError("FAILURE", `Failed to attach to Pokémon GO process: ${procName} (PID: ${pid})`);
                            }
                        }
                    } catch (e) {
                        logError("CHECK", `Error checking process ${pid}`, e);
                    }
                }, 1000); // Wait 1 second before checking
            }
        }
    });
    
    // Also hook the Zygote.forkAndSpecialize method for Android
    try {
        const zygoteClass = Java.use("com.android.internal.os.Zygote");
        
        // Try different method signatures for different Android versions
        const forkMethods = [
            "forkAndSpecialize",
            "forkAndSpecializeCommon",
            "nativeForkAndSpecialize"
        ];
        
        for (const methodName of forkMethods) {
            try {
                if (zygoteClass[methodName]) {
                    logDebug("HOOK", `Found Zygote.${methodName} method, hooking it`);
                    
                    zygoteClass[methodName].overloads.forEach(function(overload) {
                        overload.implementation = function() {
                            logDebug("FORK_JAVA", `Zygote.${methodName} called with ${arguments.length} arguments`);
                            
                            // Extract package name from arguments if possible
                            let packageName = null;
                            for (let i = 0; i < arguments.length; i++) {
                                if (typeof arguments[i] === 'string' && isPokemonGoProcess(arguments[i])) {
                                    packageName = arguments[i];
                                    break;
                                }
                            }
                            
                            // Call the original method
                            const pid = this[methodName].apply(this, arguments);
                            
                            if (pid > 0) {
                                logDebug("FORK_JAVA", `Process forked with PID: ${pid}, package: ${packageName || "unknown"}`);
                                
                                // If we know this is Pokémon GO, attach to it
                                if (packageName && isPokemonGoProcess(packageName)) {
                                    logDebug("DETECT_JAVA", `Detected Pokémon GO process: ${packageName} (PID: ${pid})`);
                                    
                                    // Use setTimeout to give the process time to initialize
                                    setTimeout(function() {
                                        if (attachToPokemonGo(pid, packageName)) {
                                            logDebug("SUCCESS_JAVA", `Successfully attached to Pokémon GO process: ${packageName} (PID: ${pid})`);
                                        } else {
                                            logError("FAILURE_JAVA", `Failed to attach to Pokémon GO process: ${packageName} (PID: ${pid})`);
                                        }
                                    }, 1000); // Wait 1 second before attaching
                                }
                            }
                            
                            return pid;
                        };
                    });
                    
                    logDebug("HOOK", `Successfully hooked Zygote.${methodName}`);
                }
            } catch (e) {
                logError("HOOK", `Error hooking Zygote.${methodName}`, e);
            }
        }
    } catch (e) {
        logError("HOOK", "Error hooking Zygote Java methods", e);
    }
    
    // Also monitor ActivityManager for process starts
    try {
        const activityManagerService = Java.use("com.android.server.am.ActivityManagerService");
        
        // Try different method names for starting processes
        const startMethods = [
            "startProcessLocked",
            "startProcess",
            "startProcessAsync"
        ];
        
        for (const methodName of startMethods) {
            try {
                if (activityManagerService[methodName]) {
                    logDebug("HOOK", `Found ActivityManagerService.${methodName} method, hooking it`);
                    
                    activityManagerService[methodName].overloads.forEach(function(overload) {
                        overload.implementation = function() {
                            logDebug("START_PROCESS", `ActivityManagerService.${methodName} called with ${arguments.length} arguments`);
                            
                            // Extract package name from arguments if possible
                            let packageName = null;
                            for (let i = 0; i < arguments.length; i++) {
                                if (typeof arguments[i] === 'string' && isPokemonGoProcess(arguments[i])) {
                                    packageName = arguments[i];
                                    break;
                                }
                            }
                            
                            // Call the original method
                            const result = this[methodName].apply(this, arguments);
                            
                            // If we know this is Pokémon GO, log it
                            if (packageName && isPokemonGoProcess(packageName)) {
                                logDebug("DETECT_AM", `ActivityManager starting Pokémon GO process: ${packageName}`);
                                
                                // The result might contain the PID
                                let pid = null;
                                if (result && typeof result === 'object' && result.pid) {
                                    pid = result.pid.value;
                                    logDebug("DETECT_AM", `Pokémon GO process started with PID: ${pid}`);
                                    
                                    // Use setTimeout to give the process time to initialize
                                    setTimeout(function() {
                                        if (attachToPokemonGo(pid, packageName)) {
                                            logDebug("SUCCESS_AM", `Successfully attached to Pokémon GO process: ${packageName} (PID: ${pid})`);
                                        } else {
                                            logError("FAILURE_AM", `Failed to attach to Pokémon GO process: ${packageName} (PID: ${pid})`);
                                        }
                                    }, 1000); // Wait 1 second before attaching
                                }
                            }
                            
                            return result;
                        };
                    });
                    
                    logDebug("HOOK", `Successfully hooked ActivityManagerService.${methodName}`);
                }
            } catch (e) {
                logError("HOOK", `Error hooking ActivityManagerService.${methodName}`, e);
            }
        }
    } catch (e) {
        logError("HOOK", "Error hooking ActivityManagerService methods", e);
    }
    
    logDebug("MONITOR", "Zygote process monitoring started successfully");
    console.log("[+] Zygote monitor running, waiting for Pokémon GO to start");
    
} catch (e) {
    logError("MONITOR", "Failed to start Zygote process monitoring", e);
    console.log("[-] Failed to start Zygote monitor: " + e.message);
}

// Helper function to get process name from PID
function getProcessName(pid) {
    try {
        const cmdline = new File(`/proc/${pid}/cmdline`, "r");
        const procName = cmdline.readText().trim();
        cmdline.close();
        return procName;
    } catch (e) {
        return null;
    }
}

// Check for already running Pokémon GO processes
function checkExistingProcesses() {
    logDebug("CHECK", "Checking for existing Pokémon GO processes");
    
    try {
        const processes = Process.enumerateProcesses();
        
        for (const proc of processes) {
            if (isPokemonGoProcess(proc.name)) {
                logDebug("EXISTING", `Found existing Pokémon GO process: ${proc.name} (PID: ${proc.pid})`);
                
                // Attach to the process
                if (attachToPokemonGo(proc.pid, proc.name)) {
                    logDebug("SUCCESS", `Successfully attached to existing Pokémon GO process: ${proc.name} (PID: ${proc.pid})`);
                } else {
                    logError("FAILURE", `Failed to attach to existing Pokémon GO process: ${proc.name} (PID: ${proc.pid})`);
                }
            }
        }
    } catch (e) {
        logError("CHECK", "Error checking existing processes", e);
    }
}

// Check for existing processes after a short delay
setTimeout(checkExistingProcesses, 2000);
