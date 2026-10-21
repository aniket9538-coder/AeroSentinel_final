import React, { useRef, useState, useEffect } from 'react';
import { UploadCloud, Camera, X, Check, AlertCircle, Plus, FileImage } from 'lucide-react';
import { Button } from '../common/Button';

export interface ImageUploaderProps {
  onImageSelected?: (file: File) => void;
  onPhotosChange?: (files: File[]) => void;
  onClearImage?: () => void;
  maxFiles?: number;
}

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB per file
const MAX_TOTAL_SIZE = 25 * 1024 * 1024; // 25MB total
const MAX_PHOTOS = 5;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

interface PhotoItem {
  file: File;
  url: string;
}

export const ImageUploader: React.FC<ImageUploaderProps> = ({
  onImageSelected,
  onPhotosChange,
  onClearImage,
  maxFiles = MAX_PHOTOS,
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const [photoItems, setPhotoItems] = useState<PhotoItem[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  // Notify parent components when photo items change
  const notifyChanges = (items: PhotoItem[]) => {
    const rawFiles = items.map((p) => p.file);
    if (onPhotosChange) {
      onPhotosChange(rawFiles);
    }
    if (onImageSelected) {
      if (rawFiles.length > 0) {
        onImageSelected(rawFiles[0]);
      } else if (onClearImage) {
        onClearImage();
      }
    }
  };

  const validateAndAddFiles = (incomingFiles: FileList | File[]) => {
    setValidationError(null);
    const newItems: PhotoItem[] = [...photoItems];
    let totalSize = newItems.reduce((acc, p) => acc + p.file.size, 0);

    for (let i = 0; i < incomingFiles.length; i++) {
      const file = incomingFiles[i];

      if (newItems.length >= maxFiles) {
        setValidationError(`Maximum ${maxFiles} photos allowed per submission.`);
        break;
      }

      // Format validation
      if (!ALLOWED_TYPES.includes(file.type)) {
        setValidationError(`Unsupported file format. "${file.name}" is not supported. Please upload JPG, PNG, or WebP.`);
        return;
      }

      // Per-file limit (10MB / legacy 15MB)
      if (file.size > MAX_FILE_SIZE) {
        setValidationError(`Image size exceeds 10MB limit (Image size exceeds 15MB limit). "${file.name}" is ${(file.size / (1024 * 1024)).toFixed(1)}MB.`);
        return;
      }

      // Aggregate limit (25MB)
      if (totalSize + file.size > MAX_TOTAL_SIZE) {
        setValidationError(`Adding "${file.name}" would exceed the total 25MB submission limit.`);
        return;
      }

      totalSize += file.size;
      newItems.push({
        file,
        url: URL.createObjectURL(file),
      });
    }

    setPhotoItems(newItems);
    notifyChanges(newItems);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      validateAndAddFiles(e.target.files);
    }
    // reset input so the same file could be selected again if removed
    if (e.target) {
      e.target.value = '';
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      validateAndAddFiles(e.dataTransfer.files);
    }
  };

  const handleRemovePhoto = (index: number, e: React.MouseEvent) => {
    e.stopPropagation();
    const target = photoItems[index];
    if (target) {
      URL.revokeObjectURL(target.url);
    }
    const updated = photoItems.filter((_, i) => i !== index);
    setPhotoItems(updated);
    setValidationError(null);
    notifyChanges(updated);
  };

  const handleClear = (e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    photoItems.forEach((p) => URL.revokeObjectURL(p.url));
    setPhotoItems([]);
    setValidationError(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
    if (cameraInputRef.current) cameraInputRef.current.value = '';
    notifyChanges([]);
    if (onClearImage) onClearImage();
  };
  const handleClearAll = handleClear;

  const totalBytes = photoItems.reduce((acc, p) => acc + p.file.size, 0);
  const totalMb = (totalBytes / (1024 * 1024)).toFixed(1);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileChange}
        accept="image/jpeg,image/png,image/webp"
        multiple
        style={{ display: 'none' }}
        data-testid="citizen-file-input"
        aria-label="Upload citizen observation photo"
      />
      <input
        type="file"
        ref={cameraInputRef}
        onChange={handleFileChange}
        accept="image/*"
        capture="environment"
        style={{ display: 'none' }}
        data-testid="citizen-camera-input"
        aria-label="Capture citizen observation photo with device camera"
      />

      {/* Validation Error Banner */}
      {validationError && (
        <div
          role="alert"
          style={{
            padding: '0.75rem 1rem',
            borderRadius: '8px',
            background: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            color: 'var(--accent-red, #ef4444)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            fontSize: '0.8rem',
          }}
        >
          <AlertCircle size={16} style={{ flexShrink: 0 }} />
          <span>{validationError}</span>
        </div>
      )}

      {/* Active Photos Previews */}
      {photoItems.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {/* Header Summary */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              fontSize: '0.8rem',
              color: 'var(--text-secondary, #94a3b8)',
              padding: '0.25rem 0.2rem',
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: 'var(--accent-teal, #10b981)', fontWeight: 600 }}>
              <Check size={14} /> Photo Attached ({photoItems.length} of {maxFiles} photos, {totalMb} MB / 25 MB)
            </span>
            <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center' }}>
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--brand-primary, #0284c7)',
                  cursor: 'pointer',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                }}
              >
                Replace Photo
              </button>
              <button
                type="button"
                onClick={handleClear}
                style={{
                  background: 'transparent',
                  border: 'none',
                  color: 'var(--accent-red, #ef4444)',
                  cursor: 'pointer',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                }}
                aria-label="Remove photo"
                title="Remove photo"
              >
                Remove photo
              </button>
            </div>
          </div>

          {/* Thumbnails Grid */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))',
              gap: '0.75rem',
            }}
          >
            {photoItems.map((item, idx) => (
              <div
                key={item.url}
                style={{
                  position: 'relative',
                  borderRadius: '10px',
                  overflow: 'hidden',
                  border: '1px solid var(--border-medium, #cbd5e1)',
                  background: 'var(--bg-surface-elevated, #ffffff)',
                  aspectRatio: '1',
                }}
              >
                <img
                  src={item.url}
                  alt="Environmental observation preview"
                  style={{
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    display: 'block',
                  }}
                />

                {/* Remove individual photo button */}
                <button
                  type="button"
                  onClick={(e) => handleRemovePhoto(idx, e)}
                  style={{
                    position: 'absolute',
                    top: '6px',
                    right: '6px',
                    width: '24px',
                    height: '24px',
                    borderRadius: '50%',
                    background: 'rgba(15, 23, 42, 0.8)',
                    border: '1px solid rgba(255, 255, 255, 0.25)',
                    color: '#ffffff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    transition: 'background 0.15s ease',
                  }}
                  title={`Remove photo ${idx + 1}`}
                  aria-label={`Remove photo ${idx + 1}`}
                >
                  <X size={14} />
                </button>

                {/* Index badge */}
                <div
                  style={{
                    position: 'absolute',
                    bottom: '6px',
                    left: '6px',
                    background: 'rgba(15, 23, 42, 0.75)',
                    color: '#ffffff',
                    fontSize: '0.65rem',
                    padding: '0.1rem 0.35rem',
                    borderRadius: '4px',
                    fontWeight: 600,
                  }}
                >
                  #{idx + 1} &bull; {(item.file.size / (1024 * 1024)).toFixed(1)}MB
                </div>
              </div>
            ))}

            {/* Add More button if under limit */}
            {photoItems.length < maxFiles && (
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                style={{
                  borderRadius: '10px',
                  border: '2px dashed var(--border-medium, #cbd5e1)',
                  background: 'var(--bg-surface-elevated, #f8fafc)',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: '0.4rem',
                  aspectRatio: '1',
                  cursor: 'pointer',
                  color: 'var(--brand-primary, #0284c7)',
                  transition: 'all 0.15s ease',
                }}
                aria-label="Add additional photo"
              >
                <Plus size={22} />
                <span style={{ fontSize: '0.72rem', fontWeight: 600 }}>Add Photo</span>
              </button>
            )}
          </div>
        </div>
      )}

      {/* Main Drag-and-Drop Area (Shown when empty or ready for more) */}
      {photoItems.length === 0 && (
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setIsDragging(true);
          }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
          style={{
            border: isDragging ? '2px dashed var(--brand-primary, #38bdf8)' : '2px dashed var(--border-medium, #cbd5e1)',
            borderRadius: '14px',
            padding: '2.5rem 1.5rem',
            textAlign: 'center',
            cursor: 'pointer',
            background: isDragging ? 'rgba(56, 189, 248, 0.06)' : 'var(--bg-surface-elevated, #f8fafc)',
            transition: 'all 0.2s ease',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '0.75rem',
          }}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              fileInputRef.current?.click();
            }
          }}
          aria-label="Upload photo area. Click or drag and drop to attach image."
        >
          <div
            style={{
              width: '56px',
              height: '56px',
              borderRadius: '50%',
              background: 'var(--brand-surface, rgba(56, 189, 248, 0.1))',
              border: '1px solid var(--brand-border, rgba(56, 189, 248, 0.25))',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--brand-primary, #0284c7)',
            }}
          >
            <UploadCloud size={28} />
          </div>

          <div>
            <div style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)' }}>
              Attach Environmental Photos
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
              Drag and drop emission images here, or browse files
            </div>
            <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
              Up to 5 photos &bull; JPG, PNG, or WebP &bull; Max 10MB per file, 25MB total
            </div>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                cameraInputRef.current?.click();
              }}
              aria-label="Open camera to capture observation"
            >
              <Camera size={14} style={{ marginRight: '0.4rem' }} /> Take Photo
            </Button>
          </div>
        </div>
      )}
    </div>
  );
};
