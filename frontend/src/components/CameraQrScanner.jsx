import { useState, useEffect, useRef, useCallback } from "react";
import { Html5Qrcode } from "html5-qrcode";
import "./CameraQrScanner.css";

function playBeep() {
  try {
    const AudioContext = window.AudioContext || window.webkitAudioContext;
    if (!AudioContext) return;
    const ctx = new AudioContext();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = "sine";
    osc.frequency.setValueAtTime(880, ctx.currentTime); // A5 note
    gain.gain.setValueAtTime(0.15, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.15);

    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.15);
  } catch (err) {
    // Audio autoplay restrictions; safely ignore
  }
}

function triggerHaptic() {
  try {
    if (typeof navigator !== "undefined" && navigator.vibrate) {
      navigator.vibrate([60, 40, 60]);
    }
  } catch (e) {
    // Unsupported or restricted; safely ignore
  }
}

export default function CameraQrScanner({ onScan, onClose }) {
  const [cameras, setCameras] = useState([]);
  const [selectedCameraId, setSelectedCameraId] = useState("");
  const [scannerState, setScannerState] = useState("INITIALIZING"); // "INITIALIZING" | "SCANNING" | "ERROR" | "SUCCESS"
  const [errorMessage, setErrorMessage] = useState("");
  const [isSecure, setIsSecure] = useState(true);

  const scannerRef = useRef(null);
  const isStoppingRef = useRef(false);
  const fileCaptureRef = useRef(null);
  const containerId = "qr-scanner-video-region";

  // Check secure context
  useEffect(() => {
    if (typeof window !== "undefined") {
      setIsSecure(window.isSecureContext !== false);
    }
  }, []);

  // Stop scanning helper
  const stopScanner = useCallback(async () => {
    if (scannerRef.current && scannerRef.current.isScanning && !isStoppingRef.current) {
      isStoppingRef.current = true;
      try {
        await scannerRef.current.stop();
        scannerRef.current.clear();
      } catch (err) {
        console.warn("Failed to stop scanner cleanly:", err);
      } finally {
        isStoppingRef.current = false;
      }
    }
  }, []);

  // Handle photo capture / file scan
  const handlePhotoCapture = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setScannerState("INITIALIZING");
    setErrorMessage("");

    try {
      await stopScanner();
      if (!scannerRef.current) {
        scannerRef.current = new Html5Qrcode(containerId);
      }

      const decodedText = await scannerRef.current.scanFile(file, true);
      if (decodedText) {
        playBeep();
        triggerHaptic();
        setScannerState("SUCCESS");
        if (onScan) {
          onScan(decodedText);
        }
      }
    } catch (err) {
      console.warn("File scan error:", err);
      setScannerState("ERROR");
      setErrorMessage(
        "Could not detect a clear QR code in that photo. Please position the camera closer to the QR code and try again."
      );
    }
  };

  // Start scanning with a chosen camera / constraints
  const startScanning = useCallback(
    async (cameraIdOrConstraint) => {
      await stopScanner();

      if (!scannerRef.current) {
        scannerRef.current = new Html5Qrcode(containerId);
      }

      setScannerState("INITIALIZING");
      setErrorMessage("");

      const config = {
        fps: 12,
        qrbox: { width: 240, height: 240 },
        aspectRatio: 1.0,
      };

      const qrSuccessCallback = (decodedText) => {
        if (isStoppingRef.current) return;
        playBeep();
        triggerHaptic();
        setScannerState("SUCCESS");

        // Stop scanning before notifying parent
        stopScanner().then(() => {
          if (onScan) {
            onScan(decodedText);
          }
        });
      };

      const qrErrorCallback = () => {
        // Continuous scan frame failures (safe to ignore)
      };

      try {
        const cameraTarget = cameraIdOrConstraint || { facingMode: "environment" };
        await scannerRef.current.start(cameraTarget, config, qrSuccessCallback, qrErrorCallback);
        setScannerState("SCANNING");
      } catch (err) {
        console.error("Camera start failed:", err);
        setScannerState("ERROR");
        const msg = String(err?.message || err);
        if (msg.toLowerCase().includes("permission") || msg.toLowerCase().includes("notallowed")) {
          setErrorMessage(
            "Camera permission was denied. Please allow camera access in your browser settings to scan QR codes."
          );
        } else if (msg.toLowerCase().includes("notfound") || msg.toLowerCase().includes("devices")) {
          setErrorMessage("No functional camera device was found on this device.");
        } else {
          setErrorMessage("Unable to access the camera. Make sure no other application is using it.");
        }
      }
    },
    [stopScanner, onScan]
  );

  // Initialize camera devices on mount
  useEffect(() => {
    let isMounted = true;

    async function initCameras() {
      try {
        // Enumerate devices
        const devices = await Html5Qrcode.getCameras();
        if (!isMounted) return;

        if (devices && devices.length > 0) {
          setCameras(devices);
          // Prefer environment/back camera if labeled
          const backCam = devices.find((d) =>
            /back|rear|environment|main/i.test(d.label)
          );
          const chosen = backCam ? backCam.id : devices[0].id;
          setSelectedCameraId(chosen);
          startScanning(chosen);
        } else {
          // Fallback to constraints
          startScanning({ facingMode: "environment" });
        }
      } catch (err) {
        if (!isMounted) return;
        // Try starting directly with constraint if getCameras failed
        startScanning({ facingMode: "environment" });
      }
    }

    initCameras();

    return () => {
      isMounted = false;
      stopScanner();
    };
  }, [startScanning, stopScanner]);

  const handleCameraChange = (e) => {
    const camId = e.target.value;
    setSelectedCameraId(camId);
    startScanning(camId);
  };

  const handleRetry = () => {
    startScanning(selectedCameraId || { facingMode: "environment" });
  };

  return (
    <div className="camera-scanner-wrap" role="dialog" aria-label="QR Code Scanner">
      <div className="camera-header-bar">
        <div className="camera-title">
          <span className="camera-title-icon" aria-hidden="true">📷</span>
          <span>Live Certificate QR Scanner</span>
        </div>

        <div className="camera-controls-row">
          {cameras.length > 1 && (
            <select
              className="camera-switch-select"
              value={selectedCameraId}
              onChange={handleCameraChange}
              aria-label="Switch camera"
            >
              {cameras.map((c, i) => (
                <option key={c.id} value={c.id}>
                  {c.label || `Camera ${i + 1}`}
                </option>
              ))}
            </select>
          )}

          {onClose && (
            <button
              type="button"
              className="camera-close-btn"
              onClick={() => {
                stopScanner();
                onClose();
              }}
              aria-label="Close camera"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      <div className="camera-viewport">
        <div id={containerId} />

        {scannerState === "SCANNING" && (
          <div className="scanner-overlay" aria-hidden="true">
            <div className="scanner-frame">
              <div className="scanner-laser" />
            </div>
            <div className="scanner-instructions">
              Point camera at certificate QR code
            </div>
          </div>
        )}

        {scannerState === "INITIALIZING" && (
          <div className="scanner-status-box">
            <span className="scanner-status-icon" aria-hidden="true">⏳</span>
            <h4 className="scanner-status-title">Initializing Camera...</h4>
            <p className="scanner-status-desc">
              Requesting camera permission to scan certificate QR codes.
            </p>
          </div>
        )}

        {scannerState === "ERROR" && (
          <div className="scanner-status-box">
            <span className="scanner-status-icon" aria-hidden="true">🚫</span>
            <h4 className="scanner-status-title">Live Video Unavailable</h4>
            <p className="scanner-status-desc">{errorMessage}</p>

            <div style={{ display: "flex", gap: "10px", marginTop: "12px", flexWrap: "wrap", justifyContent: "center" }}>
              <button
                type="button"
                className="scanner-retry-btn"
                style={{ background: "#0284c7" }}
                onClick={() => fileCaptureRef.current?.click()}
              >
                📸 Snap Photo of QR with Camera
              </button>
              <button type="button" className="scanner-retry-btn" onClick={handleRetry}>
                🔄 Retry Live Camera
              </button>
            </div>
          </div>
        )}

        {scannerState === "SUCCESS" && (
          <div className="scanner-status-box">
            <span className="scanner-status-icon" aria-hidden="true">✅</span>
            <h4 className="scanner-status-title">QR Code Detected!</h4>
            <p className="scanner-status-desc">Loading certificate details...</p>
          </div>
        )}
      </div>

      {/* Hidden file input with mobile native camera shutter */}
      <input
        ref={fileCaptureRef}
        type="file"
        accept="image/*"
        capture="environment"
        style={{ display: "none" }}
        onChange={handlePhotoCapture}
        aria-label="Capture QR Code Photo"
      />

      <div className="camera-footer-bar">
        {!isSecure && (
          <p className="camera-insecure-advisory">
            💡 <strong>Tip for mobile:</strong> Browsers require HTTPS for live video streaming on local IP. Tap below to snap directly with your phone camera:
          </p>
        )}

        <button
          type="button"
          className="camera-snap-btn"
          onClick={() => fileCaptureRef.current?.click()}
        >
          📸 Snap Photo with Mobile Camera / Pick Image
        </button>
      </div>
    </div>
  );
}
