<?php
// Define the root directory of your bot to access the srv and assets folders
$botRootDir = '/home/evanm/Bots/agape-bot/';
$assetsDir = $botRootDir . 'assets/';

// --- Asset Proxy System ---
// This intercepts requests for images/fonts and streams them to the browser
// so we don't have to expose the Linux filesystem publicly.
if (isset($_GET['type'])) {
    $type = $_GET['type'];
    $file = isset($_GET['file']) ? basename($_GET['file']) : '';
    $filePath = '';

    if ($type === 'bg') {
        $filePath = $assetsDir . 'backgrounds/' . $file;
    } else if ($type === 'frame') {
        $filePath = $assetsDir . 'frames/' . $file;
    } else if ($type === 'font') {
        // Serve the custom .ttf font
        $filePath = $assetsDir . 'fonts/' . $file;
    } else if ($type === 'pfp') {
        // Safely fetch local profile pictures uploaded to the bot
        if (isset($_GET['id'])) {
            $userId = base64_decode($_GET['id']);
            $jsonPath = $botRootDir . 'user_content/srv/' . $userId . '.json';
            if (file_exists($jsonPath)) {
                $data = json_decode(file_get_contents($jsonPath), true);
                $pfpPath = $data['photoPath'] ?? '';
                if (!str_starts_with($pfpPath, 'http') && $pfpPath !== '') {
                    $fullPfpPath = str_starts_with($pfpPath, '/') ? $pfpPath : $botRootDir . $pfpPath;
                    if (file_exists($fullPfpPath)) {
                        header('Content-Type: ' . mime_content_type($fullPfpPath));
                        readfile($fullPfpPath);
                        exit;
                    }
                }
            }
        }
        http_response_code(404);
        exit;
    }

    if (file_exists($filePath)) {
        $mime = mime_content_type($filePath);
        if ($type === 'font') {
            $mime = 'font/ttf';
            header("Access-Control-Allow-Origin: *");
        }
        header('Content-Type: ' . $mime);
        readfile($filePath);
        exit;
    } else if ($type !== 'pfp') {
        http_response_code(404);
        exit;
    }
}

// --- Profile Data Fetching ---
$encodedId = isset($_GET['id']) ? $_GET['id'] : '';
$profileData = null;

if ($encodedId) {
    // Decode the Base64 ID back into the numeric Discord ID
    $userId = base64_decode($encodedId);
    $jsonPath = $botRootDir . 'user_content/srv/' . $userId . '.json';
    
    if (file_exists($jsonPath)) {
        $jsonContent = file_get_contents($jsonPath);
        $profileData = json_decode($jsonContent, true);
    }
}

// Safely extract all profile values
$name = $profileData['name'] ?? 'Unknown';
$handle = $profileData['username'] ?? 'unknown';
$sex = isset($profileData['sex']) ? ($profileData['sex'] ? 'Female' : 'Male') : 'Unknown';
$sect = $profileData['sect'] ?? 'Unknown';
$physical = $profileData['physicalDescription'] ?? 'Unknown';
$hobbies = $profileData['hobbies'] ?? '';
$strengths = $profileData['strengths'] ?? '';
$weaknesses = $profileData['weaknesses'] ?? '';
$lookFor = $profileData['lookFor'] ?? '';
$dealBreakers = $profileData['dealBreakers'] ?? '';
$pfpUrl = $profileData['photoPath'] ?? '';

// Calculate age and birth year from birthday (stored as M/D/YYYY)
$age = '??';
$birthYear = '????';
$birthday = $profileData['birthday'] ?? null;
if ($birthday) {
    $parts = explode('/', $birthday);
    if (count($parts) === 3) {
        $month = (int)$parts[0];
        $day   = (int)$parts[1];
        $year  = (int)$parts[2];
        $birthYear = $year;
        $bd = new DateTime("{$year}-{$month}-{$day}");
        $age = (int)(new DateTime())->diff($bd)->y;
    }
}

// Route the PFP through the proxy if it's a local file, otherwise use the direct URL
$pfpRenderUrl = str_starts_with($pfpUrl, 'http') ? htmlspecialchars($pfpUrl) : "?type=pfp&id=" . urlencode($encodedId);

// Default preview background/frame
$defaultBg = 'default.png';
$defaultFrame = 'default.png';

// Fetch available dynamic backgrounds and frames
$bgFiles = [];
if (is_dir($assetsDir . 'backgrounds/')) {
    foreach (scandir($assetsDir . 'backgrounds/') as $file) {
        if ($file !== '.' && $file !== '..' && !str_starts_with(strtolower($file), 'default')) {
            $bgFiles[] = $file;
        }
    }
}

$frameFiles = [];
if (is_dir($assetsDir . 'frames/')) {
    foreach (scandir($assetsDir . 'frames/') as $file) {
        if ($file !== '.' && $file !== '..' && !str_starts_with(strtolower($file), 'default')) {
            $frameFiles[] = $file;
        }
    }
}

// Load frame configurations for dynamic sizing
$framesConfig = new stdClass();
$configPath = $assetsDir . 'frames_config.json';
if (file_exists($configPath)) {
    $parsed = json_decode(file_get_contents($configPath), true);
    if ($parsed) $framesConfig = $parsed;
}

// Load design codes config
$designCodes = ['backgrounds' => [], 'frames' => []];
$designCodesPath = $assetsDir . 'design_codes.json';
if (file_exists($designCodesPath)) {
    $parsedCodes = json_decode(file_get_contents($designCodesPath), true);
    if ($parsedCodes) $designCodes = $parsedCodes;
}

// Helper to grab a code or auto-generate a 3-letter fallback!
function getFileCode($filename, $category, $designCodes) {
    if (isset($designCodes[$category][$filename])) {
        return strtoupper($designCodes[$category][$filename]);
    }
    $nameOnly = pathinfo($filename, PATHINFO_FILENAME);
    $clean = preg_replace('/[^a-zA-Z0-9]/', '', $nameOnly);
    return strtoupper(substr($clean, 0, 3));
}

// Map backgrounds to their codes
$bgCodesMap = [];
$bgCodesMap['default.png'] = getFileCode('default.png', 'backgrounds', $designCodes);
foreach ($bgFiles as $file) {
    $bgCodesMap[$file] = getFileCode($file, 'backgrounds', $designCodes);
}

// Map frames to their codes
$frameCodesMap = [];
$frameCodesMap['default.png'] = getFileCode('default.png', 'frames', $designCodes);
foreach ($frameFiles as $file) {
    $frameCodesMap[$file] = getFileCode($file, 'frames', $designCodes);
}
?>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Matchmaking Customization Portal</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        /* Load the exact same font the Java bot uses! */
        @font-face {
            font-family: 'VAGRounded';
            src: url('?file=VAG%20Rounded%20Next%20Shine%20Regular.ttf&type=font') format('truetype');
        }

        body {
            font-family: 'VAGRounded', sans-serif;
            background-color: #1a202c;
            color: white;
        }

        .card-container {
            position: relative;
            width: 100%;
            max-width: 900px;
            margin: 0 auto;
            overflow: hidden;
            border-radius: 12px;
            box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
            background-color: #000;
        }

        .card-bg {
            position: relative;
            display: block;
            width: 100%;
            height: auto;
            z-index: 1;
        }

        .card-blob {
            position: absolute;
            top: 5%;
            left: 3%;
            background: rgba(255, 255, 255, 0.2);
            backdrop-filter: blur(4px);
            -webkit-backdrop-filter: blur(4px);
            padding: 1rem 2rem;
            border-radius: 60px;
            box-shadow: 0 4px 30px rgba(0, 0, 0, 0.1);
            display: flex;
            flex-direction: column;
            gap: 0;
            z-index: 2;
        }

        .text-stroke-white-name {
            font-family: 'Arial Rounded MT Bold', 'Arial Rounded MT', Arial, sans-serif;
            font-style: italic;
            background: linear-gradient(to right, #FF6699, #9966FF);
            -webkit-background-clip: text;
            background-clip: text;
            color: transparent;
            -webkit-text-stroke: 1.5px #FFFFFF;
            filter: drop-shadow(0px 3px 3px rgba(0,0,0,0.4));
            font-size: 3.8rem;
            line-height: 1;
            letter-spacing: 1px;
            margin-bottom: -0.2rem;
        }

        .text-stroke-white-handle {
            font-family: 'Arial Rounded MT Bold', 'Arial Rounded MT', Arial, sans-serif;
            font-style: italic;
            background: linear-gradient(to right, #FF6699, #FF9966);
            -webkit-background-clip: text;
            background-clip: text;
            color: transparent;
            -webkit-text-stroke: 1px #FFFFFF;
            filter: drop-shadow(0px 2px 2px rgba(0,0,0,0.4));
            font-size: 2.5rem;
            line-height: 1.1;
            letter-spacing: 0.5px;
        }

        .text-stroke-blue {
            color: #ebf2fa; 
            -webkit-text-stroke: 1px #1E5175; 
            filter: drop-shadow(1.5px 1.5px 0px #1E5175); 
            font-size: 1.85rem;
            line-height: 1.15;
            letter-spacing: 0.5px;
            z-index: 2;
            position: relative;
        }

        .profile-pic-container {
            position: absolute;
            top: 4.75%;
            right: 9.5%;
            width: 28%;
            aspect-ratio: 1 / 1;
            z-index: 3;
        }

        .profile-pic {
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            width: 97%;
            height: 97%;
            object-fit: cover;
        }

        .profile-frame {
            position: absolute;
            top: 50%;
            left: 50%;
            width: 97.5%; 
            height: auto; 
            transform: translate(-50%, -50%);
            z-index: 4;
            pointer-events: none;
            transition: all 0.2s ease;
        }

        .text-content-wrapper {
            position: absolute;
            top: 28%;
            left: 4%;
            width: 90%;
            z-index: 2;
            display: flex;
            flex-direction: column;
            gap: 0;
        }

        .flag-icon {
            display: inline-block;
            height: 1.2em;
            vertical-align: middle;
            margin-right: 0.3rem;
            filter: drop-shadow(2px 3px 0px rgba(0,0,0,0.4));
        }

        /* Custom Scrollbar for Browse Menu */
        ::-webkit-scrollbar {
            width: 8px;
        }
        ::-webkit-scrollbar-track {
            background: #1f2937; 
        }
        ::-webkit-scrollbar-thumb {
            background: #4b5563; 
            border-radius: 4px;
        }
        ::-webkit-scrollbar-thumb:hover {
            background: #ec4899; 
        }
    </style>
</head>
<body class="min-h-screen p-4 lg:p-8 flex justify-center">

    <!-- Main Wrapper for 2-Column Layout -->
    <div class="w-full max-w-7xl flex flex-col lg:flex-row gap-8 items-stretch">
        
        <!-- Left Sidebar: Browse Menu -->
        <div class="w-full lg:w-[360px] shrink-0 h-[60vh] lg:h-auto relative">
            <div class="w-full h-full lg:absolute lg:inset-0 bg-gray-800 rounded-xl shadow-2xl flex flex-col overflow-hidden font-sans">
                <!-- Tabs -->
                <div class="flex font-bold text-sm text-center border-b border-gray-700">
                    <button id="tabBg" class="flex-1 py-4 bg-gray-700 text-pink-400 border-b-2 border-pink-500 transition-colors">Backgrounds</button>
                    <button id="tabFrame" class="flex-1 py-4 bg-gray-900 text-gray-400 border-b-2 border-transparent hover:text-gray-200 transition-colors">Frames</button>
                </div>
            
            <!-- Tab Content -->
            <div class="flex-1 overflow-y-auto p-4">
                <!-- Backgrounds Grid -->
                <div id="gridBg" class="grid grid-cols-2 gap-4">
                    <div class="browse-item group cursor-pointer rounded-lg border-2 border-transparent transition-all duration-200" data-type="bg" data-val="default.png">
                        <div class="aspect-video bg-gray-900 rounded-t-md overflow-hidden relative">
                            <img src="?file=default.png&type=bg" class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300" loading="lazy">
                        </div>
                        <div class="text-xs text-center p-2 bg-gray-700 group-hover:bg-pink-500 group-hover:text-white transition-colors rounded-b-md truncate">Default</div>
                    </div>
                    <?php foreach ($bgFiles as $file): ?>
                        <div class="browse-item group cursor-pointer rounded-lg border-2 border-transparent transition-all duration-200" data-type="bg" data-val="<?php echo htmlspecialchars($file); ?>">
                            <div class="aspect-video bg-gray-900 rounded-t-md overflow-hidden relative">
                                <img src="?file=<?php echo urlencode($file); ?>&type=bg" class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300" loading="lazy">
                            </div>
                            <div class="text-xs text-center p-2 bg-gray-700 group-hover:bg-pink-500 group-hover:text-white transition-colors rounded-b-md truncate" title="<?php echo htmlspecialchars(ucwords(str_replace(['_', '-'], ' ', pathinfo($file, PATHINFO_FILENAME)))); ?>">
                                <?php echo htmlspecialchars(ucwords(str_replace(['_', '-'], ' ', pathinfo($file, PATHINFO_FILENAME)))); ?>
                            </div>
                        </div>
                    <?php endforeach; ?>
                </div>

                <!-- Frames Grid -->
                <div id="gridFrame" class="grid grid-cols-2 gap-4 hidden">
                    <div class="browse-item group cursor-pointer rounded-lg border-2 border-transparent transition-all duration-200" data-type="frame" data-val="default.png">
                        <div class="aspect-square bg-gray-900 rounded-t-md overflow-hidden p-2 flex items-center justify-center relative">
                            <img src="?file=default.png&type=frame" class="max-w-full max-h-full object-contain group-hover:scale-110 transition-transform duration-300" loading="lazy">
                        </div>
                        <div class="text-xs text-center p-2 bg-gray-700 group-hover:bg-pink-500 group-hover:text-white transition-colors rounded-b-md truncate">Default</div>
                    </div>
                    <?php foreach ($frameFiles as $file): ?>
                        <div class="browse-item group cursor-pointer rounded-lg border-2 border-transparent transition-all duration-200" data-type="frame" data-val="<?php echo htmlspecialchars($file); ?>">
                            <div class="aspect-square bg-gray-900 rounded-t-md overflow-hidden p-2 flex items-center justify-center relative">
                                <img src="?file=<?php echo urlencode($file); ?>&type=frame" class="max-w-full max-h-full object-contain group-hover:scale-110 transition-transform duration-300" loading="lazy">
                            </div>
                            <div class="text-xs text-center p-2 bg-gray-700 group-hover:bg-pink-500 group-hover:text-white transition-colors rounded-b-md truncate" title="<?php echo htmlspecialchars(ucwords(str_replace(['_', '-'], ' ', pathinfo($file, PATHINFO_FILENAME)))); ?>">
                                <?php echo htmlspecialchars(ucwords(str_replace(['_', '-'], ' ', pathinfo($file, PATHINFO_FILENAME)))); ?>
                            </div>
                        </div>
                    <?php endforeach; ?>
                </div>
            </div>
        </div>
    </div>

    <!-- Main Editor Area -->
    <div class="flex-1 w-full max-w-4xl bg-gray-800 rounded-xl p-6 shadow-2xl">
        <h1 class="text-3xl font-bold mb-6 text-center text-pink-400">Card Studio</h1>
            
            <?php if (!$profileData): ?>
                <div class="text-center text-red-500 font-bold text-xl py-10">
                    Profile not found! Please make sure you have generated a profile with the bot first.
                </div>
            <?php else: ?>
                <!-- The Visual Card Preview -->
                <div class="card-container mb-8">
                    <img id="cardBg" src="?file=<?php echo urlencode($defaultBg); ?>&type=bg" class="card-bg">
                    
                    <div class="card-blob">
                        <div class="text-stroke-white-name"><?php echo htmlspecialchars($name); ?></div>
                        <div class="text-stroke-white-handle"><?php echo "@" . htmlspecialchars($handle); ?></div>
                    </div>

                    <div class="text-content-wrapper text-stroke-blue">
                        <div><?php echo htmlspecialchars($age); ?> | <?php echo htmlspecialchars($birthYear); ?></div>
                        <div><?php echo htmlspecialchars($sex); ?></div>
                        <div><?php echo htmlspecialchars($sect); ?></div>
                        <div><?php echo htmlspecialchars($physical); ?></div>
                        
                        <div class="mt-4"><?php echo htmlspecialchars($hobbies); ?></div>
                        
                        <div class="mt-4">Strengths: <?php echo htmlspecialchars($strengths); ?></div>
                        <div>Weaknesses: <?php echo htmlspecialchars($weaknesses); ?></div>
                        
                        <div class="mt-4"><img src="assets/customojis/green_flag.png" class="flag-icon"> PARTNER: <?php echo htmlspecialchars($lookFor); ?></div>
                        <div><img src="assets/customojis/red_flag.png" class="flag-icon"> PARTNER: <?php echo htmlspecialchars($dealBreakers); ?></div>
                    </div>

                    <div class="profile-pic-container">
                        <img src="<?php echo $pfpRenderUrl; ?>" class="profile-pic" crossorigin="anonymous">
                        <img id="cardFrame" src="?file=<?php echo urlencode($defaultFrame); ?>&type=frame" class="profile-frame">
                    </div>
                </div>

                <!-- Submission Output -->
                <div class="bg-gray-900 p-6 rounded-lg text-center font-sans">
                    <h2 class="text-xl font-bold mb-4">Ready to Submit?</h2>
                    <p class="text-gray-400 mb-4">Copy the Design Code below and paste it back into your Discord DM with the bot!</p>
                    
                    <div class="flex items-center justify-center gap-4">
                        <code id="designCode" class="bg-gray-800 text-pink-400 px-6 py-3 rounded-lg font-mono text-xl tracking-widest border border-pink-500/30">
                            DEF-DEF
                        </code>
                        <button id="copyBtn" class="bg-pink-500 hover:bg-pink-600 text-white font-bold py-3 px-6 rounded-lg transition-colors">
                            Copy Code
                        </button>
                    </div>
                </div>

                <script>
                    // Load the JSON config directly from PHP
                    const framesConfig = <?php echo json_encode($framesConfig); ?>;
                    const fileCodes = <?php echo json_encode(['bg' => $bgCodesMap, 'frame' => $frameCodesMap]); ?>;

                    let currentBgVal = 'default.png';
                    let currentFrameVal = 'default.png';

                    const cardBg = document.getElementById('cardBg');
                    const cardFrame = document.getElementById('cardFrame');
                    const designCode = document.getElementById('designCode');
                    const copyBtn = document.getElementById('copyBtn');

                    // Browse Menu Variables
                    const tabBg = document.getElementById('tabBg');
                    const tabFrame = document.getElementById('tabFrame');
                    const gridBg = document.getElementById('gridBg');
                    const gridFrame = document.getElementById('gridFrame');
                    const browseItems = document.querySelectorAll('.browse-item');

                    // Tab Switching Logic
                    tabBg.addEventListener('click', () => {
                        gridBg.classList.remove('hidden');
                        gridFrame.classList.add('hidden');
                        tabBg.classList.add('bg-gray-700', 'text-pink-400', 'border-pink-500');
                        tabBg.classList.remove('bg-gray-900', 'text-gray-400', 'border-transparent');
                        tabFrame.classList.remove('bg-gray-700', 'text-pink-400', 'border-pink-500');
                        tabFrame.classList.add('bg-gray-900', 'text-gray-400', 'border-transparent');
                    });

                    tabFrame.addEventListener('click', () => {
                        gridFrame.classList.remove('hidden');
                        gridBg.classList.add('hidden');
                        tabFrame.classList.add('bg-gray-700', 'text-pink-400', 'border-pink-500');
                        tabFrame.classList.remove('bg-gray-900', 'text-gray-400', 'border-transparent');
                        tabBg.classList.remove('bg-gray-700', 'text-pink-400', 'border-pink-500');
                        tabBg.classList.add('bg-gray-900', 'text-gray-400', 'border-transparent');
                    });

                    // Browse Item Click Logic
                    browseItems.forEach(item => {
                        item.addEventListener('click', () => {
                            const type = item.getAttribute('data-type');
                            const val = item.getAttribute('data-val');
                            
                            if (type === 'bg') {
                                currentBgVal = val;
                            } else if (type === 'frame') {
                                currentFrameVal = val;
                            }
                            
                            updatePreview();
                        });
                    });

                    function updatePreview() {
                        const bgVal = currentBgVal;
                        const frameVal = currentFrameVal;
                        
                        cardBg.src = `?file=${bgVal}&type=bg`;
                        cardFrame.src = `?file=${frameVal}&type=frame`;
                        
                        // Apply dynamic sizing/translation to the frame based on config
                        const config = framesConfig[frameVal] || {};
                        // We use scale as the master uniform scale to prevent any warping
                        const scale = config.scale !== undefined ? config.scale : 1.16;
                        const offsetX = config.offsetX !== undefined ? config.offsetX : 0.0;
                        const offsetY = config.offsetY !== undefined ? config.offsetY : 0.0;

                        // Apply scale via transform. We MUST keep the translate(-50%, -50%) so it stays perfectly centered!
                        cardFrame.style.transform = `translate(-50%, -50%) scale(${scale})`;
                        cardFrame.style.marginLeft = (offsetX * 50) + '%';
                        cardFrame.style.marginTop = (offsetY * 50) + '%';
                        
                        const bgCode = fileCodes.bg[bgVal];
                        const frameCode = fileCodes.frame[frameVal];
                        designCode.innerText = `${bgCode}-${frameCode}`;

                        // Update Active State in Browse Menu
                        browseItems.forEach(item => {
                            const type = item.getAttribute('data-type');
                            const val = item.getAttribute('data-val');
                            
                            if ((type === 'bg' && val === bgVal) || (type === 'frame' && val === frameVal)) {
                                item.classList.add('border-pink-500', 'shadow-[0_0_15px_rgba(236,72,153,0.5)]');
                                item.classList.remove('border-transparent', 'hover:border-pink-500');
                            } else {
                                item.classList.remove('border-pink-500', 'shadow-[0_0_15px_rgba(236,72,153,0.5)]');
                                item.classList.add('border-transparent', 'hover:border-pink-500');
                            }
                        });
                    }

                    // Run once on load to ensure defaults apply
                    updatePreview();

                    copyBtn.addEventListener('click', () => {
                        navigator.clipboard.writeText(designCode.innerText).then(() => {
                            const originalText = copyBtn.innerText;
                            copyBtn.innerText = 'Copied!';
                            copyBtn.classList.remove('bg-pink-500', 'hover:bg-pink-600');
                            copyBtn.classList.add('bg-green-500', 'hover:bg-green-600');
                            
                            setTimeout(() => {
                                copyBtn.innerText = originalText;
                                copyBtn.classList.remove('bg-green-500', 'hover:bg-green-600');
                                copyBtn.classList.add('bg-pink-500', 'hover:bg-pink-600');
                            }, 2000);
                        });
                    });
                </script>
            <?php endif; ?>
        </div>
    </div>

</body>
</html>
