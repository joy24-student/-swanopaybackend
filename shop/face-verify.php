<?php require_once('header.php'); ?>

<link rel="stylesheet" href="assets/css/kyc-style.css">

<div class="kyc-wizard-container">
    <div class="container">
        <div class="row">
            <div class="col-md-8 col-md-offset-2">
                
                <div class="glass-card animate__animated animate__fadeIn">
                    
                    <div class="stepper-wrapper">
                        <div class="step-item completed">
                            <div class="step-circle"><i class="fa fa-id-card"></i></div>
                            <small>Document</small>
                        </div>
                        <div class="step-item active">
                            <div class="step-circle">2</div>
                            <small>Biometrics</small>
                        </div>
                        <div class="step-item">
                            <div class="step-circle">3</div>
                            <small>Finalize</small>
                        </div>
                    </div>

                    <div class="text-center">
                        <h2 class="mb-4">Face Verification</h2>
                        <p class="text-muted">Align your face within the circle and wait for the AI to confirm your identity.</p>
                        
                        <div class="webcam-wrapper mt-4">
                            <video id="video" width="400" height="400" autoplay muted playsinline></video>
                            <canvas id="overlay"></canvas>
                            
                            <div class="scan-line" id="scan-line"></div>
                            
                            <div class="corner-bracket top-left"></div>
                            <div class="corner-bracket top-right"></div>
                            <div class="corner-bracket bottom-left"></div>
                            <div class="corner-bracket bottom-right"></div>
                        </div>

                        <div class="mt-4">
                            <div id="status-text" class="animate__animated animate__pulse animate__infinite">
                                Initializing AI Engine...
                            </div>
                        </div>

                        <form action="kyc-process.php" method="POST" id="face-form">
                            <input type="hidden" name="nid_number" value="<?php echo $_POST['nid_number']; ?>">
                            <input type="hidden" name="nid_front_path" value="<?php echo $uploaded_nid_path; ?>">
                            
                            <input type="hidden" name="face_data" id="face_data">
                            
                            <div class="mt-5">
                                <button type="submit" id="submit-verify" class="btn-glass" disabled>
                                    <i class="fa fa-shield"></i> Submit for Review
                                </button>
                            </div>
                        </form>
                    </div>
                </div>

            </div>
        </div>
    </div>
</div>

<audio id="sound-start" src="assets/audio/scan-start.mp3"></audio>
<audio id="sound-success" src="assets/audio/success.mp3"></audio>

<script src="assets/js/face-api.min.js"></script>
<script src="assets/js/identity-scanner.js"></script>

<?php require_once('footer.php'); ?>