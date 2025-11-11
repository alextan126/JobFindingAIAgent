import React, { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDropzone } from 'react-dropzone';
import * as pdfjsLib from 'pdfjs-dist';
import { updateUserResume } from '../services/api';

// Configure PDF.js worker - use local file from public folder
pdfjsLib.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.js';

const ResumeUpload: React.FC = () => {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const navigate = useNavigate();
  const userEmail = localStorage.getItem('userEmail');

  const extractTextFromPDF = async (file: File): Promise<string> => {
    const arrayBuffer = await file.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;

    let fullText = '';
    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i);
      const textContent = await page.getTextContent();
      const pageText = textContent.items
        .map((item: any) => item.str)
        .join(' ');
      fullText += pageText + '\n';
    }

    return fullText.trim();
  };

  const onDrop = useCallback(async (acceptedFiles: File[]) => {
    if (acceptedFiles.length === 0) return;
    if (!userEmail) {
      setError('User email not found. Please log in again.');
      navigate('/login');
      return;
    }

    const file = acceptedFiles[0];
    setError('');
    setUploading(true);

    try {
      // Extract text from PDF
      const resumeText = await extractTextFromPDF(file);

      if (!resumeText || resumeText.length < 50) {
        throw new Error('Could not extract meaningful text from PDF. Please ensure your resume contains text (not just images).');
      }

      // Send to backend for OpenAI parsing
      await updateUserResume(userEmail, resumeText);

      setSuccess(true);
      setTimeout(() => {
        navigate('/dashboard');
      }, 2000);
    } catch (err: any) {
      setError(err.message || 'Failed to process resume. Please try again.');
    } finally {
      setUploading(false);
    }
  }, [userEmail, navigate]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'application/pdf': ['.pdf'],
    },
    maxFiles: 1,
    disabled: uploading,
  });

  return (
    <div style={styles.container}>
      <div style={styles.card}>
        <h1 style={styles.title}>Upload Your Resume</h1>
        <p style={styles.subtitle}>
          Upload your resume to help us find the perfect job matches for you
        </p>

        {error && <div style={styles.error}>{error}</div>}
        {success && (
          <div style={styles.success}>
            Resume processed successfully! Redirecting to dashboard...
          </div>
        )}

        <div
          {...getRootProps()}
          style={{
            ...styles.dropzone,
            ...(isDragActive ? styles.dropzoneActive : {}),
            ...(uploading ? styles.dropzoneDisabled : {}),
          }}
        >
          <input {...getInputProps()} />
          <div style={styles.uploadIcon}>📄</div>
          {uploading ? (
            <>
              <p style={styles.dropzoneText}>Processing your resume...</p>
              <p style={styles.dropzoneSubtext}>
                Extracting text and analyzing skills with AI
              </p>
            </>
          ) : isDragActive ? (
            <p style={styles.dropzoneText}>Drop your resume here...</p>
          ) : (
            <>
              <p style={styles.dropzoneText}>
                Drag and drop your resume here, or click to browse
              </p>
              <p style={styles.dropzoneSubtext}>PDF format only</p>
            </>
          )}
        </div>

        <div style={styles.infoBox}>
          <h3 style={styles.infoTitle}>What happens next?</h3>
          <ul style={styles.infoList}>
            <li>We'll extract text from your PDF resume</li>
            <li>AI will analyze your skills and experience</li>
            <li>Your profile will be updated automatically</li>
            <li>Job matches will be generated based on your background</li>
          </ul>
        </div>

        <button
          onClick={() => navigate('/dashboard')}
          style={styles.skipButton}
          disabled={uploading}
        >
          Skip for now
        </button>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f5f5f5',
    padding: '20px',
  },
  card: {
    backgroundColor: 'white',
    borderRadius: '8px',
    boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
    padding: '40px',
    maxWidth: '600px',
    width: '100%',
  },
  title: {
    fontSize: '28px',
    fontWeight: 'bold',
    marginBottom: '8px',
    textAlign: 'center',
  },
  subtitle: {
    fontSize: '14px',
    color: '#666',
    marginBottom: '24px',
    textAlign: 'center',
  },
  error: {
    backgroundColor: '#fee',
    border: '1px solid #fcc',
    color: '#c33',
    padding: '12px',
    borderRadius: '4px',
    marginBottom: '16px',
    fontSize: '14px',
  },
  success: {
    backgroundColor: '#efe',
    border: '1px solid #cfc',
    color: '#3c3',
    padding: '12px',
    borderRadius: '4px',
    marginBottom: '16px',
    fontSize: '14px',
  },
  dropzone: {
    border: '2px dashed #ddd',
    borderRadius: '8px',
    padding: '60px 40px',
    textAlign: 'center',
    cursor: 'pointer',
    transition: 'all 0.3s ease',
    marginBottom: '24px',
  },
  dropzoneActive: {
    borderColor: '#4CAF50',
    backgroundColor: '#f0f8f0',
  },
  dropzoneDisabled: {
    cursor: 'not-allowed',
    opacity: 0.6,
  },
  uploadIcon: {
    fontSize: '48px',
    marginBottom: '16px',
  },
  dropzoneText: {
    fontSize: '16px',
    fontWeight: '500',
    color: '#333',
    marginBottom: '8px',
  },
  dropzoneSubtext: {
    fontSize: '14px',
    color: '#666',
  },
  infoBox: {
    backgroundColor: '#f9f9f9',
    borderRadius: '6px',
    padding: '20px',
    marginBottom: '20px',
  },
  infoTitle: {
    fontSize: '16px',
    fontWeight: '600',
    marginBottom: '12px',
  },
  infoList: {
    fontSize: '14px',
    color: '#666',
    lineHeight: '1.8',
    paddingLeft: '20px',
  },
  skipButton: {
    width: '100%',
    padding: '12px',
    border: '1px solid #ddd',
    borderRadius: '4px',
    fontSize: '14px',
    backgroundColor: 'white',
    color: '#666',
    cursor: 'pointer',
  },
};

export default ResumeUpload;
