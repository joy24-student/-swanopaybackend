const video = document.getElementById('video');
const statusText = document.getElementById('status-text');

// 1. Load the models from your local folder
async function loadModels() {
    statusText.innerText = "Loading AI Models...";
    // Point this to the folder where you just downloaded the files
    const MODEL_URL = 'assets/js/face-models'; 

    await faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL);
    await faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL);
    await faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL);
    
    startVideo();
}

// 2. Access the Webcam
function startVideo() {
    navigator.mediaDevices.getUserMedia({ video: {} })
        .then(stream => {
            video.srcObject = stream;
            statusText.innerText = "Scanning for face...";
            // Play start sound
            document.getElementById('sound-start').play();
        })
        .catch(err => {
            statusText.innerText = "Camera Error: " + err;
            console.error(err);
        });
}

// 3. Real-time Detection Logic
video.addEventListener('play', () => {
    const canvas = faceapi.createCanvasFromMedia(video);
    const wrapper = document.querySelector('.webcam-wrapper');
    wrapper.append(canvas);

    const displaySize = { width: video.width, height: video.height };
    faceapi.matchDimensions(canvas, displaySize);

    let faceDetected = false;

    setInterval(async () => {
        if (faceDetected) return;

        const detections = await faceapi.detectAllFaces(video, 
            new faceapi.TinyFaceDetectorOptions()).withFaceLandmarks();
        
        const resizedDetections = faceapi.resizeResults(detections, displaySize);
        canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
        
        // Draw square around face
        faceapi.draw.drawDetections(canvas, resizedDetections);

        if (detections.length > 0) {
            const score = detections[0].detection.score;
            if (score > 0.6) { // Confidence threshold
                faceDetected = true;
                statusText.innerText = "Face Verified!";
                statusText.className = "text-success";
                
                // Play success sound
                document.getElementById('sound-success').play();
                
                // Pause for a second then capture the image
                setTimeout(() => captureFace(), 1000);
            }
        }
    }, 200);
});

function captureFace() {
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video, 0, 0);
    
    // Save the image data to a hidden input
    const dataURL = canvas.toDataURL('image/jpeg');
    document.getElementById('face_capture_data').value = dataURL;
    
    // Auto-advance to the next step or show "Complete"
    document.getElementById('btn-next-step').disabled = false;
    alert("Biometric Capture Complete!");
}

// Start the process
loadModels();