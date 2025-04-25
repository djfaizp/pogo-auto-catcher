import "frida-il2cpp-bridge";

Il2Cpp.perform(function() {
    console.log("[+] Frida script loaded successfully");
    console.log("[+] TRACE: Starting Pokémon GO activity tracing");

    // Get the Assembly-CSharp image
    const AssemblyCSharp = Il2Cpp.domain.assembly("Assembly-CSharp").image;
    // Get the UnityEngine image for core Unity functionality
    const UnityEngine = Il2Cpp.domain.assembly("UnityEngine").image;
    // Get the UnityEngine.CoreModule image
    const UnityEngineCoreModule = Il2Cpp.domain.assembly("UnityEngine.CoreModule").image;

    // Tracing configuration - these will be controlled by the UI
    const tracingConfig = {
        ENCOUNTER: true,
        CAPTURE: true,
        MOVEMENT: true,
        ITEM: true,
        NETWORK: true,
        GYM: true,
        POKESTOP: true,
        FRIEND: true,
        COLLECTION: true,
        RAID: true,
        AR: true,          // AR/Camera related activities
        UNITY: true,       // Unity warnings and errors
        FIREBASE: true,    // Firebase analytics
        PHYSICS: true,     // Physics-related operations
        AUTH: true         // Authentication operations
    };

    // Auto catch configuration - will be replaced by the app
    const autoCatchConfig = {
        enabled: false,
        delay: 500,
        retryOnEscape: true,  // New option to retry catching if Pokémon escapes
        maxRetries: 3         // Maximum number of retry attempts
    };

    // Auto walk configuration - will be replaced by the app
    const autoWalkConfig = {
        enabled: false,
        speed: 1.0
    };

    // Current encounter state tracking
    const currentEncounter = {
        active: false,
        pokemon: null,
        retryCount: 0,
        lastThrowTime: 0
    };

    // Helper function to log activity with a consistent format
    function logActivity(category: string, action: string, details: any = null) {
        // Check if this category is enabled in the config
        if (!tracingConfig[category]) {
            return;
        }

        const timestamp = new Date().toISOString();
        const detailsStr = details ? JSON.stringify(details) : "";
        console.log(`[TRACE][${timestamp}][${category}] ${action} ${detailsStr}`);
    }

    logActivity("INIT", "Script initialized");

    // ==================== ENCOUNTER & CAPTURE TRACING ====================

    // Find the EncounterInteractionState class
    const EncounterInteractionState =
        AssemblyCSharp.class(
            "Niantic.Holoholo.Encounter.EncounterInteractionState"
        );

    if (!EncounterInteractionState) {
        console.log("[-] Failed to find EncounterInteractionState class");
    } else {
        console.log("[+] Found EncounterInteractionState class");

        // Find the AttemptCapture method
        const AttemptCapture = EncounterInteractionState.method("AttemptCapture");

        if (!AttemptCapture) {
            console.log("[-] Failed to find AttemptCapture method");
        } else {
            console.log("[+] Found AttemptCapture method");

            // Get the ThrowStruct class from the method parameter
            const ThrowStruct = AttemptCapture.parameters[0].type.class;
            const ThrowStructFields = ThrowStruct.fields;

            console.log("[+] Found ThrowStruct class with " + ThrowStructFields.length + " fields");

            // Hook the AttemptCapture method
            // @ts-expect-error
            AttemptCapture.implementation = function(
                this: Il2Cpp.Object, obj: Il2Cpp.Object) {

                // Extract throw details
                const throwDetails = {};
                ThrowStructFields.forEach(function(f) {
                    if(!f.isStatic){
                        throwDetails[f.name] = obj.field(f.name).value;
                    }
                });

                logActivity("CAPTURE", "Pokéball throw attempt", throwDetails);

                // Find the field names for the throw parameters
                let killzoneSizeField = null;
                let curveballField = null;
                let hitKillzoneField = null;
                let missedBallField = null;

                // Identify the fields by looking at their types and names
                for (let i = 0; i < ThrowStructFields.length; i++) {
                    const field = ThrowStructFields[i];
                    if (!field.isStatic) {
                        const fieldName = field.name.toLowerCase();
                        const fieldValue = obj.field(field.name).value;

                        // Log each field for debugging
                        logActivity("CAPTURE", `Field ${i}: ${field.name} = ${fieldValue} (${typeof fieldValue})`);

                        // Try to identify fields by name and type
                        if (typeof fieldValue === 'number' &&
                            (fieldName.includes('size') || fieldName.includes('radius') || fieldName.includes('zone'))) {
                            killzoneSizeField = field;
                            logActivity("CAPTURE", `Identified killzone size field: ${field.name}`);
                        }
                        else if (typeof fieldValue === 'boolean' &&
                                (fieldName.includes('curve') || fieldName.includes('spin'))) {
                            curveballField = field;
                            logActivity("CAPTURE", `Identified curveball field: ${field.name}`);
                        }
                        else if (typeof fieldValue === 'boolean' &&
                                (fieldName.includes('hit') || fieldName.includes('target') || fieldName.includes('success'))) {
                            hitKillzoneField = field;
                            logActivity("CAPTURE", `Identified hit killzone field: ${field.name}`);
                        }
                        else if (typeof fieldValue === 'boolean' &&
                                (fieldName.includes('miss') || fieldName.includes('fail') || fieldName.includes('recover'))) {
                            missedBallField = field;
                            logActivity("CAPTURE", `Identified missed ball field: ${field.name}`);
                        }
                    }
                }

                // Use identified fields or fall back to index-based access
                if (killzoneSizeField) {
                    obj.field(killzoneSizeField.name).value = 0.00;  // EXCELLENT throw
                    logActivity("CAPTURE", `Set ${killzoneSizeField.name} to 0.00 for perfect throw`);
                } else {
                    // Fallback to index 2 as in the original code
                    obj.field(ThrowStructFields[2].name).value = 0.00;
                    logActivity("CAPTURE", `Fallback: Set field at index 2 (${ThrowStructFields[2].name}) to 0.00`);
                }

                if (curveballField) {
                    obj.field(curveballField.name).value = true;  // Enable curveball
                    logActivity("CAPTURE", `Set ${curveballField.name} to true for curveball`);
                } else {
                    // Fallback to index 3
                    obj.field(ThrowStructFields[3].name).value = true;
                    logActivity("CAPTURE", `Fallback: Set field at index 3 (${ThrowStructFields[3].name}) to true`);
                }

                if (hitKillzoneField) {
                    obj.field(hitKillzoneField.name).value = true;  // Hit the target
                    logActivity("CAPTURE", `Set ${hitKillzoneField.name} to true to hit target`);
                } else {
                    // Fallback to index 4
                    obj.field(ThrowStructFields[4].name).value = true;
                    logActivity("CAPTURE", `Fallback: Set field at index 4 (${ThrowStructFields[4].name}) to true`);
                }

                logActivity("CAPTURE", "Modified throw parameters for perfect throw");

                // Check if we need to recover a missed ball
                if (missedBallField && !obj.field(missedBallField.name).value) {
                    logActivity("CAPTURE", `Invoking AttemptCapture with modified parameters (using ${missedBallField.name})`);
                    return this.method<Il2Cpp.Object>("AttemptCapture").invoke(obj);
                } else if (ThrowStructFields[5] && !obj.field(ThrowStructFields[5].name).value) {
                    // Fallback to index 5
                    logActivity("CAPTURE", `Invoking AttemptCapture with modified parameters (using fallback field ${ThrowStructFields[5].name})`);
                    return this.method<Il2Cpp.Object>("AttemptCapture").invoke(obj);
                }

                logActivity("CAPTURE", "Recovering missed ball");
                // We need to return the result of the method call to avoid errors
                return this.method<Il2Cpp.Object>("AttemptCapture").invoke(obj);
            }

            console.log("[+] Successfully hooked AttemptCapture method");
        }

        // Hook the OnEncounterPokemon method if it exists
        try {
            const OnEncounterPokemon = EncounterInteractionState.method("OnEncounterPokemon");
            if (OnEncounterPokemon) {
                // @ts-expect-error
                OnEncounterPokemon.implementation = function(...args) {
                    const pokemonData = args.length > 0 ? args[0] : null;
                    let pokemonInfo = "Unknown Pokémon";

                    // Try to extract Pokémon information
                    if (pokemonData && pokemonData.class) {
                        try {
                            const pokemonDetails = {};

                            // Try to extract all possible fields
                            const possibleFields = [
                                "name", "pokemonName", "speciesName",
                                "id", "pokemonId", "uniqueId",
                                "cp", "combatPower",
                                "level", "pokemonLevel",
                                "hp", "hitPoints", "maxHp", "maxHitPoints",
                                "ivAttack", "attackIV", "attack",
                                "ivDefense", "defenseIV", "defense",
                                "ivStamina", "staminaIV", "stamina",
                                "gender", "pokemonGender",
                                "shiny", "isShiny",
                                "form", "pokemonForm",
                                "weather", "weatherBoosted", "isWeatherBoosted"
                            ];

                            // Extract all available fields
                            for (const fieldName of possibleFields) {
                                try {
                                    const field = pokemonData.class.field(fieldName);
                                    if (field) {
                                        pokemonDetails[fieldName] = pokemonData.field(fieldName).value;
                                    }
                                } catch (e) {
                                    // Field doesn't exist, continue
                                }
                            }

                            // Build a readable pokemon info string
                            if (pokemonDetails.name || pokemonDetails.pokemonName || pokemonDetails.speciesName) {
                                pokemonInfo = pokemonDetails.name || pokemonDetails.pokemonName || pokemonDetails.speciesName;
                            }

                            if (pokemonDetails.id || pokemonDetails.pokemonId || pokemonDetails.uniqueId) {
                                pokemonInfo += ` (ID: ${pokemonDetails.id || pokemonDetails.pokemonId || pokemonDetails.uniqueId})`;
                            }

                            if (pokemonDetails.cp || pokemonDetails.combatPower) {
                                pokemonInfo += ` CP: ${pokemonDetails.cp || pokemonDetails.combatPower}`;
                            }

                            if (pokemonDetails.level || pokemonDetails.pokemonLevel) {
                                pokemonInfo += ` Level: ${pokemonDetails.level || pokemonDetails.pokemonLevel}`;
                            }

                            if (pokemonDetails.shiny || pokemonDetails.isShiny) {
                                pokemonInfo += " ✨SHINY✨";
                            }

                            logActivity("ENCOUNTER", `Encountered ${pokemonInfo}`, pokemonDetails);

                            // Update current encounter state
                            currentEncounter.active = true;
                            currentEncounter.pokemon = pokemonInfo;
                            currentEncounter.retryCount = 0;
                            currentEncounter.lastThrowTime = Date.now();

                            // Auto catch functionality
                            if (autoCatchConfig && autoCatchConfig.enabled) {
                                logActivity("CAPTURE", "Auto catch is enabled, will throw ball after delay");

                                // Get a reference to the current instance for use in setTimeout
                                const self = this;

                                // Define a function to perform the auto throw
                                const performAutoThrow = function(controller) {
                                    try {
                                        // Find the method to throw a ball
                                        const throwBallMethod = controller.class.method("ThrowBall") ||
                                                               controller.class.method("ThrowPokeball") ||
                                                               controller.class.method("AttemptCapture") ||
                                                               controller.class.method("TryCapture");

                                        if (throwBallMethod) {
                                            logActivity("CAPTURE", "Executing auto throw");
                                            currentEncounter.lastThrowTime = Date.now();

                                            // Create a throw struct if needed
                                            if (throwBallMethod.parameters.length > 0) {
                                                const paramType = throwBallMethod.parameters[0].type;
                                                if (paramType && paramType.class) {
                                                    // Create a new instance of the parameter type
                                                    const throwParam = paramType.class.alloc();

                                                    // Set perfect throw parameters
                                                    if (throwParam.class.field("hitKillzone")) {
                                                        throwParam.field("hitKillzone").value = true;
                                                    }
                                                    if (throwParam.class.field("curveball")) {
                                                        throwParam.field("curveball").value = true;
                                                    }
                                                    if (throwParam.class.field("killzoneSize")) {
                                                        throwParam.field("killzoneSize").value = 0.00; // EXCELLENT
                                                    }

                                                    // Call the throw method with our parameter
                                                    controller.method<Il2Cpp.Object>(throwBallMethod.name).invoke(throwParam);
                                                    logActivity("CAPTURE", "Auto throw executed with throw struct");
                                                    return true;
                                                } else {
                                                    // Call without parameters
                                                    controller.method<Il2Cpp.Object>(throwBallMethod.name).invoke();
                                                    logActivity("CAPTURE", "Auto throw executed without parameters");
                                                    return true;
                                                }
                                            } else {
                                                // No parameters needed
                                                controller.method<Il2Cpp.Object>(throwBallMethod.name).invoke();
                                                logActivity("CAPTURE", "Auto throw executed without parameters");
                                                return true;
                                            }
                                        } else {
                                            logActivity("CAPTURE", "Could not find a method to throw a ball");
                                            return false;
                                        }
                                    } catch (e) {
                                        console.log(`[-] Error in auto throw: ${e}`);
                                        logActivity("CAPTURE", `Auto throw failed: ${e}`);
                                        return false;
                                    }
                                };

                                // Schedule the auto throw after the configured delay
                                setTimeout(function() {
                                    performAutoThrow(self);
                                }, autoCatchConfig.delay);
                            }
                        } catch (e) {
                            console.log(`[-] Error extracting Pokémon info: ${e}`);
                            logActivity("ENCOUNTER", `Encountered ${pokemonInfo}`);
                        }
                    } else {
                        logActivity("ENCOUNTER", `Encountered ${pokemonInfo}`);
                    }
                    return this.method<Il2Cpp.Object>("OnEncounterPokemon").invoke(...args);
                }
                console.log("[+] Successfully hooked OnEncounterPokemon method");
            }
        } catch (e) {
            console.log(`[-] Error hooking OnEncounterPokemon: ${e}`);
        }

        // Try to hook methods related to Pokémon escaping or being caught
        try {
            // Look for methods that might be called when a Pokémon escapes
            const escapeMethodNames = [
                "OnPokemonEscaped", "OnEscaped", "PokemonEscaped", "HandleEscape",
                "OnCaptureResult", "OnCaptureOutcome", "HandleCaptureResult"
            ];

            for (const methodName of escapeMethodNames) {
                try {
                    const method = EncounterInteractionState.method(methodName);
                    if (method) {
                        console.log(`[+] Found escape/capture result method: ${methodName}`);

                        // @ts-expect-error
                        method.implementation = function(...args) {
                            try {
                                // Try to determine if this is an escape or a catch
                                let isEscape = false;
                                let isCatch = false;

                                // Check method name for clues
                                if (methodName.toLowerCase().includes("escape")) {
                                    isEscape = true;
                                    logActivity("CAPTURE", "Pokémon escaped from ball");
                                } else if (methodName.toLowerCase().includes("capture")) {
                                    // Check arguments for result information
                                    if (args.length > 0) {
                                        if (typeof args[0] === 'boolean') {
                                            // Likely a success/failure boolean
                                            isCatch = args[0];
                                            isEscape = !args[0];
                                            logActivity("CAPTURE", isCatch ? "Pokémon caught successfully" : "Pokémon escaped from ball");
                                        } else if (args[0] && args[0].class) {
                                            // Try to extract result from object
                                            try {
                                                const successField = args[0].class.field("success") ||
                                                                    args[0].class.field("isSuccess") ||
                                                                    args[0].class.field("caught");
                                                if (successField) {
                                                    isCatch = args[0].field(successField.name).value;
                                                    isEscape = !isCatch;
                                                    logActivity("CAPTURE", isCatch ? "Pokémon caught successfully" : "Pokémon escaped from ball");
                                                }
                                            } catch (e) {
                                                // Couldn't extract result
                                            }
                                        }
                                    }
                                }

                                // If we detected an escape and auto-retry is enabled
                                if (isEscape && currentEncounter.active && autoCatchConfig.enabled && autoCatchConfig.retryOnEscape) {
                                    currentEncounter.retryCount++;

                                    if (currentEncounter.retryCount <= autoCatchConfig.maxRetries) {
                                        logActivity("CAPTURE", `Pokémon escaped, will retry (attempt ${currentEncounter.retryCount} of ${autoCatchConfig.maxRetries})`);

                                        // Get a reference to the current instance
                                        const self = this;

                                        // Schedule the retry throw after a delay
                                        setTimeout(function() {
                                            try {
                                                // Find the method to throw a ball
                                                const throwBallMethod = self.class.method("ThrowBall") ||
                                                                       self.class.method("ThrowPokeball") ||
                                                                       self.class.method("AttemptCapture") ||
                                                                       self.class.method("TryCapture");

                                                if (throwBallMethod) {
                                                    logActivity("CAPTURE", "Executing retry throw");
                                                    currentEncounter.lastThrowTime = Date.now();

                                                    // Create a throw struct if needed
                                                    if (throwBallMethod.parameters.length > 0) {
                                                        const paramType = throwBallMethod.parameters[0].type;
                                                        if (paramType && paramType.class) {
                                                            // Create a new instance of the parameter type
                                                            const throwParam = paramType.class.alloc();

                                                            // Set perfect throw parameters
                                                            if (throwParam.class.field("hitKillzone")) {
                                                                throwParam.field("hitKillzone").value = true;
                                                            }
                                                            if (throwParam.class.field("curveball")) {
                                                                throwParam.field("curveball").value = true;
                                                            }
                                                            if (throwParam.class.field("killzoneSize")) {
                                                                throwParam.field("killzoneSize").value = 0.00; // EXCELLENT
                                                            }

                                                            // Call the throw method with our parameter
                                                            self.method<Il2Cpp.Object>(throwBallMethod.name).invoke(throwParam);
                                                            logActivity("CAPTURE", "Retry throw executed with throw struct");
                                                        } else {
                                                            // Call without parameters
                                                            self.method<Il2Cpp.Object>(throwBallMethod.name).invoke();
                                                            logActivity("CAPTURE", "Retry throw executed without parameters");
                                                        }
                                                    } else {
                                                        // No parameters needed
                                                        self.method<Il2Cpp.Object>(throwBallMethod.name).invoke();
                                                        logActivity("CAPTURE", "Retry throw executed without parameters");
                                                    }
                                                } else {
                                                    logActivity("CAPTURE", "Could not find a method to throw a ball for retry");
                                                }
                                            } catch (e) {
                                                console.log(`[-] Error in retry throw: ${e}`);
                                                logActivity("CAPTURE", `Retry throw failed: ${e}`);
                                            }
                                        }, 1500); // Use a fixed delay for retries
                                    } else {
                                        logActivity("CAPTURE", `Maximum retry attempts (${autoCatchConfig.maxRetries}) reached, giving up`);
                                    }
                                } else if (isCatch) {
                                    // Reset encounter state when Pokémon is caught
                                    currentEncounter.active = false;
                                    currentEncounter.retryCount = 0;
                                    logActivity("CAPTURE", `Successfully caught ${currentEncounter.pokemon}`);
                                }
                            } catch (e) {
                                console.log(`[-] Error in ${methodName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked ${methodName} method`);
                    }
                } catch (e) {
                    // Method doesn't exist or couldn't be hooked
                }
            }
        } catch (e) {
            console.log(`[-] Error setting up escape/capture result tracking: ${e}`);
        }
    }

    // ==================== PLAYER MOVEMENT TRACING ====================

    try {
        // Try to find the player controller class
        const playerControllerClasses = [
            "Niantic.Holoholo.Avatar.PlayerController",
            "Niantic.Holoholo.Avatar.PlayerAvatar",
            "Niantic.Holoholo.Controls.PlayerController"
        ];

        let PlayerControllerClass = null;

        for (const className of playerControllerClasses) {
            try {
                PlayerControllerClass = AssemblyCSharp.class(className);
                if (PlayerControllerClass) {
                    console.log(`[+] Found player controller class: ${className}`);
                    break;
                }
            } catch (e) {
                // Continue trying other class names
            }
        }

        if (PlayerControllerClass) {
            // Look for methods related to movement
            const movementMethods = [
                "UpdatePosition", "SetPosition", "Move", "OnPositionChanged",
                "UpdatePlayerPosition", "SetPlayerPosition"
            ];

            for (const methodName of movementMethods) {
                try {
                    const method = PlayerControllerClass.method(methodName);
                    if (method) {
                        console.log(`[+] Found movement method: ${methodName}`);

                        // @ts-expect-error
                        method.implementation = function(...args) {
                            try {
                                let positionInfo = {};

                                // Try to extract position information from arguments
                                if (args.length > 0 && args[0]) {
                                    if (args[0].class && args[0].class.name.includes("Vector")) {
                                        // Likely a Vector3 position
                                        positionInfo = {
                                            x: args[0].field("x").value,
                                            y: args[0].field("y").value,
                                            z: args[0].field("z").value
                                        };
                                    } else if (typeof args[0] === 'number') {
                                        // Might be individual coordinates
                                        positionInfo = {
                                            x: args[0],
                                            y: args.length > 1 ? args[1] : null,
                                            z: args.length > 2 ? args[2] : null
                                        };
                                    }
                                }

                                logActivity("MOVEMENT", `Player ${methodName}`, positionInfo);
                            } catch (e) {
                                console.log(`[-] Error in ${methodName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked ${methodName} method`);
                    }
                } catch (e) {
                    // Method doesn't exist or couldn't be hooked
                }
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up movement tracking: ${e}`);
    }

    // ==================== ITEM USAGE TRACING ====================

    try {
        const itemManagerClasses = [
            "Niantic.Holoholo.Inventory.InventoryManager",
            "Niantic.Holoholo.Inventory.ItemManager",
            "Niantic.Holoholo.Items.ItemManager"
        ];

        let ItemManagerClass = null;

        for (const className of itemManagerClasses) {
            try {
                ItemManagerClass = AssemblyCSharp.class(className);
                if (ItemManagerClass) {
                    console.log(`[+] Found item manager class: ${className}`);
                    break;
                }
            } catch (e) {
                // Continue trying other class names
            }
        }

        if (ItemManagerClass) {
            // Look for methods related to item usage
            const itemMethods = [
                "UseItem", "ConsumeItem", "ApplyItem", "OnItemUsed"
            ];

            for (const methodName of itemMethods) {
                try {
                    const method = ItemManagerClass.method(methodName);
                    if (method) {
                        console.log(`[+] Found item method: ${methodName}`);

                        // @ts-expect-error
                        method.implementation = function(...args) {
                            try {
                                let itemInfo = "Unknown Item";

                                // Try to extract item information from arguments
                                if (args.length > 0 && args[0]) {
                                    if (typeof args[0] === 'number') {
                                        itemInfo = `Item ID: ${args[0]}`;
                                    } else if (args[0].class) {
                                        // Try to get item name or ID from object
                                        const nameField = args[0].class.field("name") || args[0].class.field("itemName");
                                        const idField = args[0].class.field("id") || args[0].class.field("itemId");

                                        if (nameField) {
                                            itemInfo = args[0].field(nameField.name).value;
                                        }

                                        if (idField) {
                                            itemInfo += ` (ID: ${args[0].field(idField.name).value})`;
                                        }
                                    }
                                }

                                logActivity("ITEM", `${methodName} - ${itemInfo}`);
                            } catch (e) {
                                console.log(`[-] Error in ${methodName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked ${methodName} method`);
                    }
                } catch (e) {
                    // Method doesn't exist or couldn't be hooked
                }
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up item usage tracking: ${e}`);
    }

    // ==================== NETWORK ACTIVITY TRACING ====================

    try {
        const networkClasses = [
            "Niantic.Holoholo.Network.NetworkClient",
            "Niantic.Holoholo.Network.HttpClient",
            "Niantic.Platform.Network.NetworkClient"
        ];

        for (const className of networkClasses) {
            try {
                const NetworkClass = AssemblyCSharp.class(className);
                if (NetworkClass) {
                    console.log(`[+] Found network class: ${className}`);

                    // Look for methods related to network requests
                    const networkMethods = [
                        "SendRequest", "Request", "Post", "Get", "SendMessage"
                    ];

                    for (const methodName of networkMethods) {
                        try {
                            const method = NetworkClass.method(methodName);
                            if (method) {
                                console.log(`[+] Found network method: ${methodName}`);

                                // @ts-expect-error
                                method.implementation = function(...args) {
                                    try {
                                        let requestInfo = "Unknown Request";

                                        // Try to extract request information
                                        if (args.length > 0) {
                                            if (typeof args[0] === 'string') {
                                                requestInfo = args[0];
                                            } else if (args[0].class) {
                                                // Try to get URL or endpoint
                                                const urlField = args[0].class.field("url") || args[0].class.field("endpoint");
                                                if (urlField) {
                                                    requestInfo = args[0].field(urlField.name).value;
                                                }
                                            }
                                        }

                                        logActivity("NETWORK", `${methodName} - ${requestInfo}`);
                                    } catch (e) {
                                        console.log(`[-] Error in ${methodName} hook: ${e}`);
                                    }

                                    return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                                };

                                console.log(`[+] Successfully hooked ${methodName} method`);
                            }
                        } catch (e) {
                            // Method doesn't exist or couldn't be hooked
                        }
                    }
                }
            } catch (e) {
                // Class doesn't exist
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up network tracking: ${e}`);
    }

    // ==================== GYM & RAID BATTLE TRACING ====================

    try {
        const gymClasses = [
            "Niantic.Holoholo.Gym.GymInteractionController",
            "Niantic.Holoholo.Gym.GymBattleController",
            "Niantic.Holoholo.Gym.GymManager"
        ];

        let GymClass = null;

        for (const className of gymClasses) {
            try {
                GymClass = AssemblyCSharp.class(className);
                if (GymClass) {
                    console.log(`[+] Found gym class: ${className}`);
                    break;
                }
            } catch (e) {
                // Continue trying other class names
            }
        }

        if (GymClass) {
            // Look for methods related to gym interactions
            const gymMethods = [
                "EnterGym", "StartBattle", "EndBattle", "OnGymEntered", "OnBattleStarted",
                "OnBattleEnded", "OnRaidStarted", "OnRaidEnded", "JoinRaid"
            ];

            for (const methodName of gymMethods) {
                try {
                    const method = GymClass.method(methodName);
                    if (method) {
                        console.log(`[+] Found gym method: ${methodName}`);

                        // @ts-expect-error
                        method.implementation = function(...args) {
                            try {
                                let gymInfo = "Unknown Gym";

                                // Try to extract gym information from arguments
                                if (args.length > 0 && args[0] && args[0].class) {
                                    // Try to get gym name or ID
                                    const nameField = args[0].class.field("name") || args[0].class.field("gymName");
                                    const idField = args[0].class.field("id") || args[0].class.field("gymId");

                                    if (nameField) {
                                        gymInfo = args[0].field(nameField.name).value;
                                    }

                                    if (idField) {
                                        gymInfo += ` (ID: ${args[0].field(idField.name).value})`;
                                    }
                                }

                                // Determine if this is a gym or raid activity
                                const category = methodName.toLowerCase().includes("raid") ? "RAID" : "GYM";
                                logActivity(category, `${methodName} - ${gymInfo}`);
                            } catch (e) {
                                console.log(`[-] Error in ${methodName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked ${methodName} method`);
                    }
                } catch (e) {
                    // Method doesn't exist or couldn't be hooked
                }
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up gym tracking: ${e}`);
    }

    // ==================== POKESTOP INTERACTION TRACING ====================

    try {
        const pokestopClasses = [
            "Niantic.Holoholo.Pokestop.PokestopController",
            "Niantic.Holoholo.Pokestop.PokestopInteractionController",
            "Niantic.Holoholo.Map.MapPokestopController"
        ];

        let PokestopClass = null;

        for (const className of pokestopClasses) {
            try {
                PokestopClass = AssemblyCSharp.class(className);
                if (PokestopClass) {
                    console.log(`[+] Found pokestop class: ${className}`);
                    break;
                }
            } catch (e) {
                // Continue trying other class names
            }
        }

        if (PokestopClass) {
            // Look for methods related to pokestop interactions
            const pokestopMethods = [
                "SpinPokestop", "OnPokestopSpun", "InteractWithPokestop",
                "OnPokestopInteraction", "OnItemsReceived"
            ];

            for (const methodName of pokestopMethods) {
                try {
                    const method = PokestopClass.method(methodName);
                    if (method) {
                        console.log(`[+] Found pokestop method: ${methodName}`);

                        // @ts-expect-error
                        method.implementation = function(...args) {
                            try {
                                let pokestopInfo = "Unknown Pokéstop";

                                // Try to extract pokestop information from arguments
                                if (args.length > 0 && args[0] && args[0].class) {
                                    // Try to get pokestop name or ID
                                    const nameField = args[0].class.field("name") || args[0].class.field("pokestopName");
                                    const idField = args[0].class.field("id") || args[0].class.field("pokestopId");

                                    if (nameField) {
                                        pokestopInfo = args[0].field(nameField.name).value;
                                    }

                                    if (idField) {
                                        pokestopInfo += ` (ID: ${args[0].field(idField.name).value})`;
                                    }
                                }

                                logActivity("POKESTOP", `${methodName} - ${pokestopInfo}`);
                            } catch (e) {
                                console.log(`[-] Error in ${methodName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked ${methodName} method`);
                    }
                } catch (e) {
                    // Method doesn't exist or couldn't be hooked
                }
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up pokestop tracking: ${e}`);
    }

    // ==================== FRIEND INTERACTION TRACING ====================

    try {
        const friendClasses = [
            "Niantic.Holoholo.Social.FriendManager",
            "Niantic.Holoholo.Social.FriendController",
            "Niantic.Holoholo.Friends.FriendManager"
        ];

        let FriendClass = null;

        for (const className of friendClasses) {
            try {
                FriendClass = AssemblyCSharp.class(className);
                if (FriendClass) {
                    console.log(`[+] Found friend class: ${className}`);
                    break;
                }
            } catch (e) {
                // Continue trying other class names
            }
        }

        if (FriendClass) {
            // Look for methods related to friend interactions
            const friendMethods = [
                "SendGift", "OpenGift", "AddFriend", "RemoveFriend",
                "AcceptFriendRequest", "OnGiftSent", "OnGiftOpened"
            ];

            for (const methodName of friendMethods) {
                try {
                    const method = FriendClass.method(methodName);
                    if (method) {
                        console.log(`[+] Found friend method: ${methodName}`);

                        // @ts-expect-error
                        method.implementation = function(...args) {
                            try {
                                let friendInfo = "Unknown Friend";

                                // Try to extract friend information from arguments
                                if (args.length > 0 && args[0]) {
                                    if (typeof args[0] === 'string') {
                                        friendInfo = args[0];
                                    } else if (args[0].class) {
                                        // Try to get friend name or ID
                                        const nameField = args[0].class.field("name") ||
                                                         args[0].class.field("friendName") ||
                                                         args[0].class.field("trainerName");
                                        const idField = args[0].class.field("id") ||
                                                       args[0].class.field("friendId") ||
                                                       args[0].class.field("trainerId");

                                        if (nameField) {
                                            friendInfo = args[0].field(nameField.name).value;
                                        }

                                        if (idField) {
                                            friendInfo += ` (ID: ${args[0].field(idField.name).value})`;
                                        }
                                    }
                                }

                                logActivity("FRIEND", `${methodName} - ${friendInfo}`);
                            } catch (e) {
                                console.log(`[-] Error in ${methodName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked ${methodName} method`);
                    }
                } catch (e) {
                    // Method doesn't exist or couldn't be hooked
                }
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up friend tracking: ${e}`);
    }

    // ==================== POKEMON COLLECTION TRACING ====================

    try {
        const collectionClasses = [
            "Niantic.Holoholo.Inventory.PokemonManager",
            "Niantic.Holoholo.Pokemon.PokemonCollection",
            "Niantic.Holoholo.Pokemon.PokemonManager"
        ];

        let CollectionClass = null;

        for (const className of collectionClasses) {
            try {
                CollectionClass = AssemblyCSharp.class(className);
                if (CollectionClass) {
                    console.log(`[+] Found collection class: ${className}`);
                    break;
                }
            } catch (e) {
                // Continue trying other class names
            }
        }

        if (CollectionClass) {
            // Look for methods related to pokemon collection
            const collectionMethods = [
                "TransferPokemon", "EvolvePokemon", "PowerUpPokemon",
                "RenamePokemon", "FavoritePokemon", "OnPokemonTransferred",
                "OnPokemonEvolved", "OnPokemonPoweredUp"
            ];

            for (const methodName of collectionMethods) {
                try {
                    const method = CollectionClass.method(methodName);
                    if (method) {
                        console.log(`[+] Found collection method: ${methodName}`);

                        // @ts-expect-error
                        method.implementation = function(...args) {
                            try {
                                let pokemonInfo = "Unknown Pokémon";

                                // Try to extract pokemon information from arguments
                                if (args.length > 0 && args[0]) {
                                    if (typeof args[0] === 'string' || typeof args[0] === 'number') {
                                        pokemonInfo = `ID: ${args[0]}`;
                                    } else if (args[0].class) {
                                        // Try to get pokemon name or ID
                                        const nameField = args[0].class.field("name") ||
                                                         args[0].class.field("pokemonName") ||
                                                         args[0].class.field("speciesName");
                                        const idField = args[0].class.field("id") ||
                                                       args[0].class.field("pokemonId") ||
                                                       args[0].class.field("uniqueId");
                                        const cpField = args[0].class.field("cp") ||
                                                       args[0].class.field("combatPower");

                                        if (nameField) {
                                            pokemonInfo = args[0].field(nameField.name).value;
                                        }

                                        if (idField) {
                                            pokemonInfo += ` (ID: ${args[0].field(idField.name).value})`;
                                        }

                                        if (cpField) {
                                            pokemonInfo += ` CP: ${args[0].field(cpField.name).value}`;
                                        }
                                    }
                                }

                                logActivity("COLLECTION", `${methodName} - ${pokemonInfo}`);
                            } catch (e) {
                                console.log(`[-] Error in ${methodName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked ${methodName} method`);
                    }
                } catch (e) {
                    // Method doesn't exist or couldn't be hooked
                }
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up collection tracking: ${e}`);
    }

    // ==================== AR/CAMERA TRACING ====================

    try {
        // Look for AR-related classes
        const arClasses = [
            "Niantic.Holoholo.AR.ArSessionService",
            "Niantic.Lightship.AR.Core.LightshipUnityContext",
            "Niantic.Holoholo.AR.ArPlusDeviceService"
        ];

        for (const className of arClasses) {
            try {
                const ArClass = AssemblyCSharp.class(className);
                if (ArClass) {
                    console.log(`[+] Found AR class: ${className}`);

                    // Look for methods related to AR initialization and camera
                    const arMethods = [
                        "Initialize", "Start", "Stop", "CheckSupport", "TriggerDeviceCompatibilityCheckAsync",
                        "OnCameraPermissionGranted", "OnCameraPermissionDenied"
                    ];

                    for (const methodName of arMethods) {
                        try {
                            const method = ArClass.method(methodName);
                            if (method) {
                                console.log(`[+] Found AR method: ${methodName}`);

                                // @ts-expect-error
                                method.implementation = function(...args) {
                                    try {
                                        logActivity("AR", `${className}.${methodName} called`);
                                    } catch (e) {
                                        console.log(`[-] Error in AR ${methodName} hook: ${e}`);
                                    }

                                    return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                                };

                                console.log(`[+] Successfully hooked AR method: ${methodName}`);
                            }
                        } catch (e) {
                            // Method doesn't exist or couldn't be hooked
                        }
                    }
                }
            } catch (e) {
                // Class doesn't exist
            }
        }

        // Try to hook Unity's Camera class
        try {
            const CameraClass = UnityEngineCoreModule.class("UnityEngine.Camera");
            if (CameraClass) {
                console.log("[+] Found UnityEngine.Camera class");

                const cameraMethods = ["Render", "RenderWithShader", "SetTargetTexture"];

                for (const methodName of cameraMethods) {
                    try {
                        const method = CameraClass.method(methodName);
                        if (method) {
                            console.log(`[+] Found Camera method: ${methodName}`);

                            // @ts-expect-error
                            method.implementation = function(...args) {
                                try {
                                    logActivity("AR", `Camera.${methodName} called`);
                                } catch (e) {
                                    console.log(`[-] Error in Camera ${methodName} hook: ${e}`);
                                }

                                return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                            };

                            console.log(`[+] Successfully hooked Camera method: ${methodName}`);
                        }
                    } catch (e) {
                        // Method doesn't exist or couldn't be hooked
                    }
                }
            }
        } catch (e) {
            console.log(`[-] Error finding Camera class: ${e}`);
        }
    } catch (e) {
        console.log(`[-] Error setting up AR tracking: ${e}`);
    }

    // ==================== UNITY ERROR/WARNING TRACKING ====================

    try {
        // Hook Unity's Debug class to capture logs, warnings, and errors
        const DebugClass = UnityEngineCoreModule.class("UnityEngine.Debug");
        if (DebugClass) {
            console.log("[+] Found UnityEngine.Debug class");

            const debugMethods = ["Log", "LogWarning", "LogError", "LogException"];

            for (const methodName of debugMethods) {
                try {
                    const method = DebugClass.method(methodName);
                    if (method) {
                        console.log(`[+] Found Debug method: ${methodName}`);

                        // @ts-expect-error
                        method.implementation = function(...args) {
                            try {
                                let message = "Unknown message";
                                if (args.length > 0) {
                                    if (typeof args[0] === 'string') {
                                        message = args[0];
                                    } else if (args[0] && args[0].toString) {
                                        message = args[0].toString();
                                    }
                                }

                                let logLevel = "INFO";
                                if (methodName === "LogWarning") logLevel = "WARNING";
                                if (methodName === "LogError" || methodName === "LogException") logLevel = "ERROR";

                                logActivity("UNITY", `${logLevel}: ${message}`);
                            } catch (e) {
                                console.log(`[-] Error in Debug ${methodName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked Debug method: ${methodName}`);
                    }
                } catch (e) {
                    // Method doesn't exist or couldn't be hooked
                }
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up Unity debug tracking: ${e}`);
    }

    // ==================== FIREBASE ANALYTICS TRACKING ====================

    try {
        // Look for Firebase-related classes
        const firebaseClasses = [
            "Firebase.Analytics.FirebaseAnalytics",
            "Firebase.Analytics.FirebaseAnalyticsInternal",
            "Firebase.Analytics.FirebaseAnalyticsClient"
        ];

        for (const className of firebaseClasses) {
            try {
                // Firebase might be in a different assembly
                const assemblies = Il2Cpp.domain.assemblies;
                for (const assembly of assemblies) {
                    try {
                        const FirebaseClass = assembly.image.class(className);
                        if (FirebaseClass) {
                            console.log(`[+] Found Firebase class: ${className} in ${assembly.name}`);

                            // Look for methods related to Firebase analytics
                            const firebaseMethods = [
                                "LogEvent", "SetUserProperty", "SetUserId", "SetSessionTimeoutDuration"
                            ];

                            for (const methodName of firebaseMethods) {
                                try {
                                    const method = FirebaseClass.method(methodName);
                                    if (method) {
                                        console.log(`[+] Found Firebase method: ${methodName}`);

                                        // @ts-expect-error
                                        method.implementation = function(...args) {
                                            try {
                                                let eventInfo = "Unknown event";
                                                if (args.length > 0 && typeof args[0] === 'string') {
                                                    eventInfo = args[0];

                                                    // If there are parameters, try to capture them
                                                    if (args.length > 1 && args[1] && args[1].class) {
                                                        eventInfo += " with parameters";
                                                    }
                                                }

                                                logActivity("FIREBASE", `${methodName}: ${eventInfo}`);
                                            } catch (e) {
                                                console.log(`[-] Error in Firebase ${methodName} hook: ${e}`);
                                            }

                                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                                        };

                                        console.log(`[+] Successfully hooked Firebase method: ${methodName}`);
                                    }
                                } catch (e) {
                                    // Method doesn't exist or couldn't be hooked
                                }
                            }
                        }
                    } catch (e) {
                        // Class doesn't exist in this assembly
                    }
                }
            } catch (e) {
                // Error finding class
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up Firebase tracking: ${e}`);
    }

    // ==================== PHYSICS OPERATIONS TRACKING ====================

    try {
        // Hook Unity's Rigidbody class to capture physics operations
        const RigidbodyClass = UnityEngineCoreModule.class("UnityEngine.Rigidbody");
        if (RigidbodyClass) {
            console.log("[+] Found UnityEngine.Rigidbody class");

            // Properties and methods to hook
            const rigidbodyProperties = [
                "set_velocity", "set_angularVelocity", "set_isKinematic"
            ];

            const rigidbodyMethods = [
                "AddForce", "AddTorque", "MovePosition", "MoveRotation"
            ];

            // Hook property setters
            for (const propertyName of rigidbodyProperties) {
                try {
                    const property = RigidbodyClass.method(propertyName);
                    if (property) {
                        console.log(`[+] Found Rigidbody property: ${propertyName}`);

                        // @ts-expect-error
                        property.implementation = function(...args) {
                            try {
                                let valueInfo = "Unknown value";
                                if (args.length > 0) {
                                    if (typeof args[0] === 'boolean') {
                                        valueInfo = args[0] ? "true" : "false";
                                    } else if (args[0] && args[0].class && args[0].class.name.includes("Vector")) {
                                        valueInfo = `Vector(${args[0].field("x").value}, ${args[0].field("y").value}, ${args[0].field("z").value})`;
                                    }
                                }

                                logActivity("PHYSICS", `Rigidbody.${propertyName}: ${valueInfo}`);
                            } catch (e) {
                                console.log(`[-] Error in Rigidbody ${propertyName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(propertyName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked Rigidbody property: ${propertyName}`);
                    }
                } catch (e) {
                    // Property doesn't exist or couldn't be hooked
                }
            }

            // Hook methods
            for (const methodName of rigidbodyMethods) {
                try {
                    const method = RigidbodyClass.method(methodName);
                    if (method) {
                        console.log(`[+] Found Rigidbody method: ${methodName}`);

                        // @ts-expect-error
                        method.implementation = function(...args) {
                            try {
                                logActivity("PHYSICS", `Rigidbody.${methodName} called`);
                            } catch (e) {
                                console.log(`[-] Error in Rigidbody ${methodName} hook: ${e}`);
                            }

                            return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                        };

                        console.log(`[+] Successfully hooked Rigidbody method: ${methodName}`);
                    }
                } catch (e) {
                    // Method doesn't exist or couldn't be hooked
                }
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up physics tracking: ${e}`);
    }

    // ==================== AUTHENTICATION TRACKING ====================

    try {
        // Look for authentication-related classes
        const authClasses = [
            "Niantic.Holoholo.Authentication.AuthenticationManager",
            "Niantic.Holoholo.Authentication.AuthenticationService",
            "Niantic.Platform.Authentication.AuthenticationClient",
            "Niantic.Holoholo.WebAuthRunner"
        ];

        for (const className of authClasses) {
            try {
                const AuthClass = AssemblyCSharp.class(className);
                if (AuthClass) {
                    console.log(`[+] Found authentication class: ${className}`);

                    // Look for methods related to authentication
                    const authMethods = [
                        "Login", "Logout", "Authenticate", "RefreshToken",
                        "OnLoginSuccess", "OnLoginFailure", "startFlow"
                    ];

                    for (const methodName of authMethods) {
                        try {
                            const method = AuthClass.method(methodName);
                            if (method) {
                                console.log(`[+] Found auth method: ${methodName}`);

                                // @ts-expect-error
                                method.implementation = function(...args) {
                                    try {
                                        // Don't log sensitive information like tokens
                                        logActivity("AUTH", `${className}.${methodName} called`);
                                    } catch (e) {
                                        console.log(`[-] Error in auth ${methodName} hook: ${e}`);
                                    }

                                    return this.method<Il2Cpp.Object>(methodName).invoke(...args);
                                };

                                console.log(`[+] Successfully hooked auth method: ${methodName}`);
                            }
                        } catch (e) {
                            // Method doesn't exist or couldn't be hooked
                        }
                    }
                }
            } catch (e) {
                // Class doesn't exist
            }
        }
    } catch (e) {
        console.log(`[-] Error setting up authentication tracking: ${e}`);
    }

    logActivity("INIT", "All hooks installed successfully");
});
