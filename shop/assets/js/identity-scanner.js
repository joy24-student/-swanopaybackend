// ADVANCED LIVENESS DETECTION & AUTO CAPTURE
const video = document.getElementById('video');
const canvas = document.getElementById('canvas');
const statusText = document.getElementById('status-text');
let isCapturing = false;

// 1. LOAD MODELS
async function startVideoLogic() {
    if(!video) return; // Safety check
    
    statusText.innerHTML = "Loading AI Models...";
    await Promise.all([
        faceapi.nets.tinyFaceDetector.loadFromUri('assets/models'),
        faceapi.nets.faceLandmark68Net.loadFromUri('assets/models'),
        faceapi.nets.faceRecognitionNet.loadFromUri('assets/models'),
        faceapi.nets.faceExpressionNet.loadFromUri('assets/models')
    ]);
    
    startStream();
}

function startStream() {
    navigator.mediaDevices.getUserMedia({ video: {} })
        .then(stream => { video.srcObject = stream; })
        .catch(err => { 
            console.error(err);
            statusText.innerHTML = "Camera Access Denied";
        });
}

// 2. DETECTION LOOP
video.addEventListener('play', () => {
    const displaySize = { width: video.width, height: video.height };
    faceapi.matchDimensions(canvas, displaySize);

    statusText.innerHTML = "Align face in circle...";

    setInterval(async () => {
        if(isCapturing) return; // Stop logic if capturing

        const detections = await faceapi.detectAllFaces(video, new faceapi.TinyFaceDetectorOptions())
            .withFaceLandmarks()
            .withFaceExpressions();

        const resizedDetections = faceapi.resizeResults(detections, displaySize);
        canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
        faceapi.draw.drawDetections(canvas, resizedDetections);

        if (detections.length > 0) {
            const expressions = detections[0].expressions;
            
            // LIVENESS CHECK: Happy > 0.7 OR Neutral > 0.9
            if (expressions.happy > 0.7 || expressions.neutral > 0.9) {
                statusText.innerHTML = '<span style="color:#27ae60">Face Perfect! Holding...</span>';
                
                // AUTO CAPTURE
                isCapturing = true;
                setTimeout(processCapture, 800); // 800ms delay for stability
            } else {
                statusText.innerHTML = "Please Smile or hold still...";
            }
        }
    }, 100);
});

// 3. SERVER UPLOAD
function processCapture() {
    statusText.innerHTML = '<i class="fa fa-spinner fa-spin"></i> Verified! Saving...';
    
    // Draw Frame
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, 320, 320);
    const dataURL = canvas.toDataURL('image/jpeg');

    let formData = new FormData();
    formData.append('face_image', dataURL);
    formData.append('process_face_capture', '1');

    fetch('merchant-register.php', { method: 'POST', body: formData })
    .then(res => res.json())
    .then(data => {
        if(data.success) {
            // Play Sound
            let audio = document.getElementById('sound-success');
            if(audio) audio.play();

            statusText.innerHTML = '<span style="color:#27ae60"><i class="fa fa-check-circle"></i> Biometrics Saved!</span>';
            
            // Stop Camera
            video.pause();
            video.srcObject.getTracks().forEach(track => track.stop());
            
            // Enable Next
            document.getElementById('btn-face-next').disabled = false;
        } else {
            isCapturing = false; // Retry
            statusText.innerHTML = "Error saving. Retrying...";
        }
    })
    .catch(err => {
        console.error(err);
        isCapturing = false;
        statusText.innerHTML = "Connection failed.";
    });
}