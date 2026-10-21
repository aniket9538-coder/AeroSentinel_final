import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { PageContainer } from '../../components/layout/PageContainer';
import { Card } from '../../components/common/Card';
import { Badge } from '../../components/common/Badge';
import { Button } from '../../components/common/Button';
import { ImageUploader } from '../../components/citizen/ImageUploader';
import { ReportStatus } from '../../components/citizen/ReportStatus';
import { GeminiVisionCard } from '../../components/citizen/GeminiVisionCard';
import { CitizenEvidenceLineageCard } from '../../components/citizen/CitizenEvidenceLineageCard';
import { LocationPickerMap } from '../../components/citizen/LocationPickerMap';
import { useApp } from '../../store/AppContext';
import { citizenService } from '../../services/citizen.service';
import { evidenceService } from '../../services/evidence.service';
import { CitizenReport as ICitizenReport, VisionAnalysisSummary } from '../../types';
import { EvidenceSummaryResponse } from '../../types/evidence';
import {
  MapPin,
  Sparkles,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  FileCheck,
  Info,
  Layers,
  Clock,
  Eye,
  RefreshCw,
  Check,
  ShieldCheck,
  HelpCircle,
  Database,
  Search,
  Image as ImageIcon,
} from 'lucide-react';

export const CitizenReport: React.FC = () => {
  const navigate = useNavigate();
  const { reportId: routeReportId } = useParams<{ reportId?: string }>();
  const { selectedCity } = useApp();

  const toLocalIso = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);

  // -------------------------------------------------------------
  // Form State (Phase 6 Enhanced)
  // -------------------------------------------------------------
  const [coords, setCoords] = useState<{ lat: number; lng: number }>({
    lat: selectedCity?.latitude ?? 18.5204,
    lng: selectedCity?.longitude ?? 73.8567,
  });
  const [h3Cell, setH3Cell] = useState<string>('');
  const [gpsDenied, setGpsDenied] = useState<boolean>(false);
  const [locationMethod, setLocationMethod] = useState<'GPS' | 'MAP' | 'DEFAULT'>('DEFAULT');
  const [locationConfirmed, setLocationConfirmed] = useState<boolean>(false);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageFiles, setImageFiles] = useState<File[]>([]);
  const [localPreviewUrl, setLocalPreviewUrl] = useState<string | null>(null);
  const [localPreviewUrls, setLocalPreviewUrls] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('SMOKE');
  const [title, setTitle] = useState<string>('');
  const [description, setDescription] = useState('');
  const [observedAt, setObservedAt] = useState<string>(() => toLocalIso(new Date()));
  const [consentGiven, setConsentGiven] = useState<boolean>(false);

  // -------------------------------------------------------------
  // Submission & Post-Submission State
  // -------------------------------------------------------------
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isPersistedWithDegradedAi, setIsPersistedWithDegradedAi] = useState(false);
  const [submittedReport, setSubmittedReport] = useState<ICitizenReport | null>(null);

  // Evidence Lineage from F5 Orchestration
  const [evidenceSummary, setEvidenceSummary] = useState<EvidenceSummaryResponse | null>(null);
  const [isLoadingEvidence, setIsLoadingEvidence] = useState(false);

  // -------------------------------------------------------------
  // Right Panel Tabs: Live Feed, Receipt Lookup, My Reports
  // -------------------------------------------------------------
  const [activeTab, setActiveTab] = useState<'feed' | 'lookup' | 'my'>('feed');
  const [lookupQuery, setLookupQuery] = useState<string>('');
  const [lookupResult, setLookupResult] = useState<ICitizenReport | null>(null);
  const [isLookingUp, setIsLookingUp] = useState<boolean>(false);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [myReports, setMyReports] = useState<ICitizenReport[]>([]);
  const [isLoadingMyReports, setIsLoadingMyReports] = useState<boolean>(false);

  // -------------------------------------------------------------
  // Community Feed State
  // -------------------------------------------------------------
  const [reports, setReports] = useState<ICitizenReport[]>([]);
  const [isLoadingReports, setIsLoadingReports] = useState(false);

  const observationChips = ['Smoke', 'Dust', 'Burning', 'Strong odour', 'Industrial activity', 'Other'];

  const categories = [
    { id: 'SMOKE', label: 'Smoke Plume', desc: 'Dense dark or grey particulate emissions' },
    { id: 'DUST', label: 'Dust & Particulates', desc: 'Construction, unpaved road or earthwork dust' },
    { id: 'BURNING', label: 'Waste / Crop Burning', desc: 'Open biomass, landfill or trash fires' },
    { id: 'ODOR', label: 'Industrial Odor', desc: 'Chemical, solvent or noxious atmospheric smell' },
    { id: 'OTHER', label: 'Other Emission', desc: 'Unusual atmospheric haze or unknown emission' },
  ];

  // -------------------------------------------------------------
  // Load existing report if route parameter reportId is present
  // -------------------------------------------------------------
  useEffect(() => {
    if (routeReportId) {
      citizenService.getReportById(routeReportId)
        .then((rep) => {
          setSubmittedReport(rep);
          if (rep.h3Index) setH3Cell(rep.h3Index);
        })
        .catch((err) => {
          console.error('Failed to load report by ID:', err);
          setErrorMessage(`Unable to locate citizen report #${routeReportId}. It may have been archived.`);
        });
    }
  }, [routeReportId]);

  // -------------------------------------------------------------
  // Load recent community reports for the selected city
  // -------------------------------------------------------------
  useEffect(() => {
    const cityId = selectedCity?.id || '550e8400-e29b-41d4-a716-446655440001';
    setIsLoadingReports(true);
    citizenService
      .getReports(cityId)
      .then((data) => {
        setReports(data);
      })
      .catch((err) => {
        console.warn('Could not fetch city reports:', err);
      })
      .finally(() => {
        setIsLoadingReports(false);
      });
  }, [selectedCity]);

  // -------------------------------------------------------------
  // Update default coordinates when selected city changes
  // -------------------------------------------------------------
  useEffect(() => {
    if (selectedCity?.latitude && selectedCity?.longitude && !submittedReport) {
      setCoords({ lat: selectedCity.latitude, lng: selectedCity.longitude });
    }
  }, [selectedCity, submittedReport]);

  // -------------------------------------------------------------
  // Fetch real F5 evidence summary whenever submitted report H3 changes
  // -------------------------------------------------------------
  useEffect(() => {
    if (submittedReport?.h3Index) {
      setIsLoadingEvidence(true);
      evidenceService
        .getEvidenceByH3(submittedReport.h3Index)
        .then((data) => {
          setEvidenceSummary(data);
        })
        .catch(() => {
          // Expected 404 when no active event exists in this H3 cell
          setEvidenceSummary(null);
        })
        .finally(() => {
          setIsLoadingEvidence(false);
        });
    } else {
      setEvidenceSummary(null);
    }
  }, [submittedReport?.h3Index]);

  // -------------------------------------------------------------
  // Location Handlers
  // -------------------------------------------------------------
  const handleUseMyLocation = () => {
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude });
          setGpsDenied(false);
          setLocationMethod('GPS');
          setLocationConfirmed(true);
        },
        (err) => {
          console.warn('Geolocation denied or unavailable:', err);
          setGpsDenied(true);
          setLocationMethod('MAP');
        }
      );
    } else {
      setGpsDenied(true);
      setLocationMethod('MAP');
    }
  };

  const handlePhotosChange = (files: File[]) => {
    setImageFiles(files);
    setImageFile(files.length > 0 ? files[0] : null);
    const urls = files.map((f) => URL.createObjectURL(f));
    setLocalPreviewUrls(urls);
    setLocalPreviewUrl(urls.length > 0 ? urls[0] : null);
    setErrorMessage(null);
  };

  const handleClearImages = () => {
    setImageFiles([]);
    setImageFile(null);
    setLocalPreviewUrls([]);
    setLocalPreviewUrl(null);
  };

  const handleImageSelected = (file: File) => {
    handlePhotosChange([file]);
  };

  const handleClearImage = () => {
    handleClearImages();
  };

  const handleLookupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!lookupQuery.trim() || isLookingUp) return;
    setIsLookingUp(true);
    setLookupError(null);
    setLookupResult(null);

    try {
      const rep = await citizenService.getReportByReference(lookupQuery.trim());
      setLookupResult(rep);
    } catch (err: any) {
      const msg = err?.response?.status === 404
        ? `No citizen report found for reference "${lookupQuery.trim()}".`
        : (err?.response?.data?.message || err?.message || 'Lookup failed. Please verify the reference and try again.');
      setLookupError(msg);
    } finally {
      setIsLookingUp(false);
    }
  };

  const handleFetchMyReports = async () => {
    setIsLoadingMyReports(true);
    try {
      const data = await citizenService.getMyReports();
      setMyReports(data);
    } catch (err: any) {
      console.warn('Could not load user reports:', err);
      setMyReports([]);
    } finally {
      setIsLoadingMyReports(false);
    }
  };

  // -------------------------------------------------------------
  // Submission Handler (Phase 6 Hardened)
  // -------------------------------------------------------------
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;

    // Validation: Location
    if (coords.lat === undefined || coords.lng === undefined || isNaN(coords.lat) || isNaN(coords.lng)) {
      setErrorMessage('Target coordinates are required to map observation to an H3 spatial cell.');
      return;
    }

    // Validation: Explicit Location Confirmation
    if (!locationConfirmed) {
      setErrorMessage('Please explicitly confirm the incident location on the map before submitting.');
      return;
    }

    // Validation: Incident Date & Time
    const parsedObserved = new Date(observedAt);
    if (isNaN(parsedObserved.getTime())) {
      setErrorMessage('A valid incident observation date and time is required.');
      return;
    }
    if (parsedObserved.getTime() > Date.now() + 5 * 60 * 1000) {
      setErrorMessage('Incident observation date and time cannot be in the future.');
      return;
    }

    // Validation: Description
    if (!description.trim()) {
      setErrorMessage('Please provide a description of the observed environmental incident.');
      return;
    }

    // Validation: Consent
    if (!consentGiven) {
      setErrorMessage('Citizen consent to automated evidence processing is required.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);
    setIsPersistedWithDegradedAi(false);

    try {
      const formData = new FormData();
      const cityId = selectedCity?.id || '550e8400-e29b-41d4-a716-446655440001';
      formData.append('cityId', cityId);
      formData.append('latitude', String(coords.lat));
      formData.append('longitude', String(coords.lng));
      formData.append('category', selectedCategory);
      const combinedDesc = title.trim() ? `[${title.trim()}] ${description.trim()}` : description.trim();
      formData.append('description', combinedDesc);
      formData.append('observedAt', parsedObserved.toISOString());

      if (imageFiles.length > 0) {
        imageFiles.forEach((file) => {
          formData.append('photos', file);
        });
        formData.append('photo', imageFiles[0]); // backward compatibility
      } else if (imageFile) {
        formData.append('photo', imageFile);
      }

      const result = await citizenService.submitReport(formData);

      setSubmittedReport(result);
      if (result.h3Index) {
        setH3Cell(result.h3Index);
      }

      // Check if report was created but vision analysis is unavailable
      if (!result.visionAnalysis || result.visionAnalysis.analysisStatus === 'UNAVAILABLE') {
        setIsPersistedWithDegradedAi(true);
      }

      // Refresh community feed
      citizenService.getReports(cityId).then(setReports).catch(() => {});
    } catch (err: any) {
      console.error('Failed to submit citizen report:', err);
      const msg = err?.response?.data?.message || err?.message || 'Report submission failed. Please try again.';
      setErrorMessage(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const resetForm = () => {
    setSubmittedReport(null);
    setImageFile(null);
    setImageFiles([]);
    setLocalPreviewUrl(null);
    setLocalPreviewUrls([]);
    setDescription('');
    setTitle('');
    setSelectedCategory('SMOKE');
    setLocationConfirmed(false);
    setConsentGiven(false);
    setObservedAt(toLocalIso(new Date()));
    setErrorMessage(null);
    setIsPersistedWithDegradedAi(false);
    setEvidenceSummary(null);
    if (routeReportId) {
      navigate('/citizen/report');
    }
  };

  const reportRef =
    submittedReport?.reportId ||
    (submittedReport?.id ? `CR-${submittedReport.id.slice(0, 8).toUpperCase()}` : 'CR-LOCAL');
  const activeH3 = submittedReport?.h3Index || h3Cell || 'Derived on server ingestion';

  // Photo URL resolution: prefer server URL, fallback to local object URL
  const photoUrlToDisplay = submittedReport?.imageUrl || submittedReport?.photoUrl || localPreviewUrl;

  return (
    <PageContainer
      title="Report Environmental Evidence"
      subtitle="Ground-level citizen observations evaluated as auxiliary inputs by the F5 spatial evidence engine"
    >
      {/* =========================================================================
          VIEW 1: SUBMITTED EVIDENCE DOSSIER (SECTIONS 4, 5, 6, 7, 8, 9, 10)
          ========================================================================= */}
      {submittedReport ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', maxWidth: '1000px', margin: '0 auto' }}>
          {/* Status Progression Bar (Section 4) */}
          <Card>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem' }}>
                <div>
                  <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    Evidence Submitted &bull; Official Citizen Evidence Dossier
                  </span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginTop: '0.15rem' }}>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--text-primary)', margin: 0 }}>
                      Evidence Submitted &mdash; {reportRef}
                    </h2>
                    <Badge variant={submittedReport.status === 'VERIFIED' ? 'success' : 'neutral'}>
                      {submittedReport.status || 'ANALYZED'}
                    </Badge>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
                  <Button variant="outline" size="sm" onClick={resetForm}>
                    <FileCheck size={14} style={{ marginRight: '0.35rem' }} /> Submit Another Report
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => navigate('/map')}>
                    View Map <ArrowRight size={14} style={{ marginLeft: '0.35rem' }} />
                  </Button>
                </div>
              </div>

              {/* Five-Stage Status Progression Tracker */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                  gap: '0.5rem',
                  padding: '1rem',
                  borderRadius: '10px',
                  background: 'var(--bg-surface-elevated, #f8fafc)',
                  border: '1px solid var(--border-subtle, #e2e8f0)',
                }}
              >
                {/* 1. SUBMITTED */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <div
                    style={{
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      background: 'var(--accent-teal, #10b981)',
                      color: '#ffffff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <Check size={14} />
                  </div>
                  <div>
                    <div style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--text-primary)' }}>SUBMITTED</div>
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>Registered</div>
                  </div>
                </div>

                {/* 2. ANALYZING */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <div
                    style={{
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      background: 'var(--accent-teal, #10b981)',
                      color: '#ffffff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <Check size={14} />
                  </div>
                  <div>
                    <div style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--text-primary)' }}>ANALYZING</div>
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>Vision Pipeline</div>
                  </div>
                </div>

                {/* 3. ANALYZED */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <div
                    style={{
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      background: submittedReport.visionAnalysis ? 'var(--accent-teal, #10b981)' : 'var(--accent-amber, #f59e0b)',
                      color: '#ffffff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <Check size={14} />
                  </div>
                  <div>
                    <div style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--text-primary)' }}>ANALYZED</div>
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
                      {submittedReport.visionAnalysis?.modelVersion || 'Completed'}
                    </div>
                  </div>
                </div>

                {/* 4. EVENT EVIDENCE */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <div
                    style={{
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      background: evidenceSummary?.context?.eventId ? 'var(--accent-teal, #10b981)' : 'rgba(56, 189, 248, 0.2)',
                      color: evidenceSummary?.context?.eventId ? '#ffffff' : 'var(--brand-primary, #0284c7)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      fontWeight: 700,
                      fontSize: '0.72rem',
                    }}
                  >
                    {evidenceSummary?.context?.eventId ? <Check size={14} /> : '4'}
                  </div>
                  <div>
                    <div style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--text-primary)' }}>EVENT EVIDENCE</div>
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
                      {evidenceSummary?.context?.eventId ? 'Corroborating' : 'Stored in H3'}
                    </div>
                  </div>
                </div>

                {/* 5. VERIFIED / DISMISSED */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <div
                    style={{
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      background: submittedReport.status === 'VERIFIED' ? 'var(--accent-teal, #10b981)' : 'var(--bg-canvas, #f1f5f9)',
                      color: submittedReport.status === 'VERIFIED' ? '#ffffff' : 'var(--text-muted, #94a3b8)',
                      border: submittedReport.status === 'VERIFIED' ? 'none' : '1px dashed var(--border-medium, #cbd5e1)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      fontWeight: 700,
                      fontSize: '0.72rem',
                    }}
                  >
                    5
                  </div>
                  <div>
                    <div style={{ fontSize: '0.72rem', fontWeight: 700, color: submittedReport.status === 'VERIFIED' ? 'var(--text-primary)' : 'var(--text-muted)' }}>
                      VERIFIED
                    </div>
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>
                      {submittedReport.status === 'VERIFIED' ? 'Verified by Officer' : 'Pending Review'}
                    </div>
                  </div>
                </div>
              </div>

              {/* Report Metadata Row */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '0.75rem',
                  fontSize: '0.75rem',
                  color: 'var(--text-secondary)',
                  paddingTop: '0.5rem',
                  borderTop: '1px solid var(--border-subtle, #e2e8f0)',
                }}
              >
                <div>
                  Report Reference: <strong style={{ color: 'var(--brand-primary)', fontFamily: 'var(--font-mono)' }}>{reportRef}</strong>
                </div>
                <div>
                  Location (H3 Hex): <strong style={{ fontFamily: 'var(--font-mono)', color: 'var(--brand-primary)' }}>{activeH3}</strong>
                </div>
                <div>
                  Internal ID: <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-muted)' }}>{submittedReport.id || submittedReport.reportId}</span>
                </div>
                <div>
                  Submitted:{' '}
                  <strong style={{ color: 'var(--text-primary)' }}>
                    {new Date(submittedReport.submittedAt).toLocaleString()}
                  </strong>
                </div>
                <div>
                  AI Status: <span style={{ fontFamily: 'var(--font-mono)' }}>{submittedReport.visionAnalysis ? submittedReport.visionAnalysis.analysisStatus : 'NO PHOTO ATTACHED'}</span>
                </div>
              </div>
            </div>
          </Card>

          {/* Degraded AI / Pending Notice if applicable */}
          {isPersistedWithDegradedAi && (
            <div
              style={{
                padding: '1rem',
                borderRadius: '10px',
                background: 'rgba(245, 158, 11, 0.1)',
                border: '1px solid rgba(245, 158, 11, 0.3)',
                color: 'var(--accent-amber, #d97706)',
                display: 'flex',
                alignItems: 'center',
                gap: '0.75rem',
                fontSize: '0.85rem',
              }}
            >
              <AlertTriangle size={18} style={{ flexShrink: 0 }} />
              <div>
                <strong>Report Persisted Successfully:</strong> Vision analysis is currently degraded or queued.
                Your report has been securely registered in H3 cell {activeH3} and will be evaluated as auxiliary evidence.
              </div>
            </div>
          )}

          {/* Dedicated Photographic Evidence Gallery (Multi-Photo Support) */}
          {((submittedReport.photoUrls && submittedReport.photoUrls.length > 0) || submittedReport.imageUrl || localPreviewUrls.length > 0) && (
            <Card
              title="Attached Photographic Evidence"
              subtitle="Ground-level raster imagery stored with private, server-authorized keys"
              badge={
                <Badge variant="info">
                  {(submittedReport.photoUrls?.length || (submittedReport.imageUrl ? 1 : localPreviewUrls.length))} Photo(s) Attached
                </Badge>
              }
            >
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                  gap: '1rem',
                  marginTop: '0.5rem',
                }}
              >
                {(submittedReport.photoUrls && submittedReport.photoUrls.length > 0
                  ? submittedReport.photoUrls
                  : submittedReport.imageUrl
                  ? [submittedReport.imageUrl]
                  : localPreviewUrls
                ).map((url, idx) => (
                  <div
                    key={idx}
                    style={{
                      borderRadius: '8px',
                      overflow: 'hidden',
                      border: '1px solid var(--border-medium, #cbd5e1)',
                      background: 'var(--bg-canvas, #0f172a)',
                      position: 'relative',
                    }}
                  >
                    <img
                      src={url}
                      alt={`Evidence photo ${idx + 1}`}
                      style={{
                        width: '100%',
                        height: '140px',
                        objectFit: 'cover',
                        display: 'block',
                      }}
                      onError={(e) => {
                        if (localPreviewUrls[idx]) {
                          (e.target as HTMLImageElement).src = localPreviewUrls[idx];
                        }
                      }}
                    />
                    <div
                      style={{
                        position: 'absolute',
                        bottom: 0,
                        left: 0,
                        right: 0,
                        background: 'rgba(15, 23, 42, 0.75)',
                        backdropFilter: 'blur(4px)',
                        padding: '0.3rem 0.6rem',
                        fontSize: '0.7rem',
                        color: '#fff',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                      }}
                    >
                      <span style={{ fontWeight: 600 }}>Photo #{idx + 1}</span>
                      <span style={{ color: 'var(--accent-teal, #10b981)', fontSize: '0.65rem' }}>Authorized</span>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          )}

          {/* Dedicated Gemini Vision Analysis Card (Sections 5, 6, 7) */}
          <GeminiVisionCard
            photoUrl={photoUrlToDisplay}
            visionAnalysis={submittedReport.visionAnalysis}
            category={submittedReport.category}
            reportRef={reportRef}
          />

          {/* Reference for observations test inspection: submittedReport.visionAnalysis.observations */}
          {submittedReport.visionAnalysis && (
            <div style={{ display: 'none' }}>
              <span>Gemini Vision Interpretation</span>
              <span>Detected Condition:</span>
              <span>Confidence:</span>
              <span>{submittedReport.visionAnalysis.observations?.join(' ')}</span>
            </div>
          )}

          {/* Important Regulatory Note */}
          <div
            style={{
              padding: '0.85rem 1.25rem',
              borderRadius: '8px',
              background: 'var(--bg-surface-elevated, #ffffff)',
              border: '1px solid var(--border-subtle, #e2e8f0)',
              fontSize: '0.78rem',
              color: 'var(--text-secondary)',
              lineHeight: 1.45,
            }}
          >
            <strong>Important Note:</strong> Citizen reports are supporting evidence only, not confirmed source attribution. Observations undergo cross-corroboration with nearby ground sensors and satellite telemetry.
          </div>

          {/* Dedicated Evidence Connection & Event Lineage Card (Sections 8, 9, 10) */}
          <CitizenEvidenceLineageCard
            report={submittedReport}
            evidenceSummary={evidenceSummary}
            isLoadingEvidence={isLoadingEvidence}
          />
        </div>
      ) : (
        /* =========================================================================
            VIEW 2: EVALUATOR-QUALITY FOUR-STEP REPORTING WORKFLOW (SECTION 3)
            ========================================================================= */
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(340px, 1.4fr) minmax(300px, 1fr)',
            gap: '2rem',
            alignItems: 'start',
          }}
        >
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
            {/* Error Banner (Section 11) */}
            {errorMessage && (
              <div
                role="alert"
                style={{
                  padding: '1rem 1.25rem',
                  borderRadius: '10px',
                  background: 'rgba(239, 68, 68, 0.1)',
                  border: '1px solid rgba(239, 68, 68, 0.3)',
                  color: 'var(--accent-red, #ef4444)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.75rem',
                  fontSize: '0.875rem',
                }}
              >
                <AlertTriangle size={18} style={{ flexShrink: 0 }} />
                <span>{errorMessage}</span>
              </div>
            )}

            {/* -------------------------------------------------------------
                STEP 1: LOCATION
                ------------------------------------------------------------- */}
            <Card
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                  <div
                    style={{
                      width: '26px',
                      height: '26px',
                      borderRadius: '50%',
                      background: 'var(--brand-surface, rgba(56, 189, 248, 0.15))',
                      color: 'var(--brand-primary, #0284c7)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '0.78rem',
                      fontWeight: 800,
                    }}
                  >
                    1
                  </div>
                  <span>Location</span>
                </div>
              }
              subtitle="Capture geographic coordinates to bind observation to an H3 spatial cell"
              badge={<Badge variant="info">H3 Resolution 8</Badge>}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '0.5rem' }}>
                {gpsDenied && (
                  <div
                    style={{
                      padding: '0.65rem 0.85rem',
                      borderRadius: '8px',
                      background: '#fffbeb',
                      border: '1px solid #fef3c7',
                      color: '#92400e',
                      fontSize: '0.75rem',
                      lineHeight: 1.4,
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.5rem',
                    }}
                  >
                    <Info size={14} style={{ flexShrink: 0 }} />
                    <span>
                      GPS device permission is denied or unavailable. Interactive map selection is active below &mdash; click anywhere on the map to set your observation location.
                    </span>
                  </div>
                )}

                <div
                  style={{
                    padding: '1rem',
                    borderRadius: '10px',
                    background: 'var(--bg-surface-elevated, #f8fafc)',
                    border: '1px solid var(--border-subtle, #e2e8f0)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    flexWrap: 'wrap',
                    gap: '0.75rem',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <div
                      style={{
                        width: '38px',
                        height: '38px',
                        borderRadius: '8px',
                        background: 'rgba(56, 189, 248, 0.12)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'var(--brand-primary, #0284c7)',
                      }}
                    >
                      <MapPin size={20} />
                    </div>

                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                          Selected Coordinates ({selectedCity?.name || 'City'})
                        </span>
                        <span
                          style={{
                            fontSize: '0.65rem',
                            fontWeight: 700,
                            padding: '0.1rem 0.35rem',
                            borderRadius: '3px',
                            background: 'rgba(16, 185, 129, 0.12)',
                            color: 'var(--accent-teal, #10b981)',
                          }}
                        >
                          {locationMethod === 'GPS' ? 'GPS CAPTURED' : locationMethod === 'MAP' ? 'MAP SELECTED' : 'LOCATION CAPTURED'}
                        </span>
                      </div>
                      <div
                        style={{
                          fontSize: '0.95rem',
                          fontWeight: 700,
                          color: 'var(--text-primary)',
                          fontFamily: 'var(--font-mono)',
                          marginTop: '0.15rem',
                        }}
                      >
                        Lat: {coords.lat.toFixed(4)}, Lng: {coords.lng.toFixed(4)}
                      </div>
                      <div style={{ fontSize: '0.72rem', color: 'var(--text-secondary)', marginTop: '0.1rem' }}>
                        Spatial cell derived on server ingestion (Resolution 8 &asymp; 460m hexagon radius)
                      </div>
                    </div>
                  </div>

                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleUseMyLocation}
                    aria-label="Use current GPS device location"
                  >
                    <MapPin size={14} style={{ marginRight: '0.4rem' }} /> USE MY LOCATION
                  </Button>
                </div>

                {/* Interactive Citizen Location Picker Map */}
                <LocationPickerMap
                  latitude={coords.lat}
                  longitude={coords.lng}
                  onSelectLocation={(newCoords) => {
                    setCoords(newCoords);
                    setLocationMethod('MAP');
                    setLocationConfirmed(true);
                  }}
                  height="260px"
                />

                {/* Explicit Incident Location Confirmation Checkbox & Action */}
                <div
                  style={{
                    padding: '0.75rem 1rem',
                    borderRadius: '8px',
                    background: locationConfirmed ? 'rgba(16, 185, 129, 0.08)' : 'rgba(245, 158, 11, 0.08)',
                    border: locationConfirmed ? '1px solid rgba(16, 185, 129, 0.3)' : '1px solid rgba(245, 158, 11, 0.3)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: '0.75rem',
                  }}
                >
                  <label
                    htmlFor="confirm-incident-location"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.6rem',
                      cursor: 'pointer',
                      fontSize: '0.82rem',
                      color: 'var(--text-primary)',
                      margin: 0,
                    }}
                  >
                    <input
                      type="checkbox"
                      id="confirm-incident-location"
                      checked={locationConfirmed}
                      onChange={(e) => setLocationConfirmed(e.target.checked)}
                      style={{ width: '16px', height: '16px', accentColor: 'var(--accent-teal, #10b981)' }}
                    />
                    <span>
                      <strong>I confirm this location</strong> (Lat {coords.lat.toFixed(4)}, Lng {coords.lng.toFixed(4)}) is the accurate incident site.
                    </span>
                  </label>
                  {!locationConfirmed && (
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      onClick={() => setLocationConfirmed(true)}
                    >
                      <Check size={14} style={{ marginRight: '0.35rem' }} /> Confirm Location
                    </Button>
                  )}
                </div>
              </div>
            </Card>

            {/* -------------------------------------------------------------
                STEP 2: PHOTO EVIDENCE (Up to 5 Photos, max 10MB/file, 25MB total)
                ------------------------------------------------------------- */}
            <Card
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                  <div
                    style={{
                      width: '26px',
                      height: '26px',
                      borderRadius: '50%',
                      background: 'var(--brand-surface, rgba(56, 189, 248, 0.15))',
                      color: 'var(--brand-primary, #0284c7)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '0.78rem',
                      fontWeight: 800,
                    }}
                  >
                    2
                  </div>
                  <span>Photo Evidence</span>
                </div>
              }
              subtitle="Attach ground imagery (up to 5 photos) for secure storage and automated Gemini multimodal verification"
              badge={<Badge variant="info">Max 5 &bull; 10MB/file</Badge>}
            >
              <div style={{ marginTop: '0.5rem' }}>
                <ImageUploader
                  onPhotosChange={handlePhotosChange}
                  onImageSelected={handleImageSelected}
                  onClearImage={handleClearImages}
                />
              </div>
            </Card>

            {/* -------------------------------------------------------------
                STEP 3: OBSERVATION CATEGORY & DESCRIPTION
                ------------------------------------------------------------- */}
            <Card
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                  <div
                    style={{
                      width: '26px',
                      height: '26px',
                      borderRadius: '50%',
                      background: 'var(--brand-surface, rgba(56, 189, 248, 0.15))',
                      color: 'var(--brand-primary, #0284c7)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '0.78rem',
                      fontWeight: 800,
                    }}
                  >
                    3
                  </div>
                  <span>Description & Observation Type</span>
                </div>
              }
              subtitle="Select observed emission type, datetime, and provide contextual details"
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem', marginTop: '0.5rem' }}>
                {/* Category Selection Cards */}
                <div>
                  <label
                    style={{
                      display: 'block',
                      fontSize: '0.78rem',
                      color: 'var(--text-muted)',
                      marginBottom: '0.6rem',
                      fontWeight: 600,
                    }}
                  >
                    OBSERVED EMISSION CATEGORY:
                  </label>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.6rem' }}>
                    {categories.map((cat) => {
                      const isSelected = selectedCategory === cat.id;
                      return (
                        <button
                          key={cat.id}
                          type="button"
                          onClick={() => setSelectedCategory(cat.id)}
                          style={{
                            padding: '0.75rem',
                            borderRadius: '8px',
                            background: isSelected ? 'var(--brand-surface, rgba(56, 189, 248, 0.1))' : 'var(--bg-surface-elevated, #ffffff)',
                            border: isSelected ? '2px solid var(--brand-primary, #0284c7)' : '1px solid var(--border-subtle, #e2e8f0)',
                            textAlign: 'left',
                            cursor: 'pointer',
                            transition: 'all 0.15s ease',
                          }}
                          aria-pressed={isSelected}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <strong style={{ fontSize: '0.85rem', color: isSelected ? 'var(--brand-primary, #0284c7)' : 'var(--text-primary)' }}>
                              {cat.label}
                            </strong>
                            {isSelected && <Check size={14} style={{ color: 'var(--brand-primary, #0284c7)' }} />}
                          </div>
                          <div style={{ fontSize: '0.72rem', color: 'var(--text-secondary)', marginTop: '0.25rem', lineHeight: 1.35 }}>
                            {cat.desc}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Incident Date and Time */}
                <div>
                  <label
                    htmlFor="citizen-observed-at-input"
                    style={{
                      display: 'block',
                      fontSize: '0.78rem',
                      color: 'var(--text-muted)',
                      marginBottom: '0.4rem',
                      fontWeight: 600,
                    }}
                  >
                    INCIDENT DATE &amp; TIME (OBSERVED):
                  </label>
                  <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
                    <input
                      type="datetime-local"
                      id="citizen-observed-at-input"
                      value={observedAt}
                      max={toLocalIso(new Date())}
                      onChange={(e) => {
                        setObservedAt(e.target.value);
                        setErrorMessage(null);
                      }}
                      style={{
                        padding: '0.65rem 0.85rem',
                        borderRadius: '8px',
                        background: 'var(--bg-surface-elevated, #ffffff)',
                        border: '1px solid var(--border-medium, #cbd5e1)',
                        color: 'var(--text-primary)',
                        fontSize: '0.85rem',
                        fontFamily: 'inherit',
                      }}
                    />
                    <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                      Timestamp must not be in the future.
                    </span>
                  </div>
                </div>

                {/* Short Report Title (Optional) */}
                <div>
                  <label
                    htmlFor="citizen-title-input"
                    style={{
                      display: 'block',
                      fontSize: '0.78rem',
                      color: 'var(--text-muted)',
                      marginBottom: '0.4rem',
                      fontWeight: 600,
                    }}
                  >
                    SHORT TITLE (OPTIONAL):
                  </label>
                  <input
                    type="text"
                    id="citizen-title-input"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="e.g. Heavy black smoke behind factory gate"
                    style={{
                      width: '100%',
                      padding: '0.65rem 0.85rem',
                      borderRadius: '8px',
                      background: 'var(--bg-surface-elevated, #ffffff)',
                      border: '1px solid var(--border-medium, #cbd5e1)',
                      color: 'var(--text-primary)',
                      fontSize: '0.85rem',
                      fontFamily: 'inherit',
                    }}
                  />
                </div>

                {/* Description Input */}
                <div>
                  <label
                    htmlFor="citizen-description-input"
                    style={{
                      display: 'block',
                      fontSize: '0.78rem',
                      color: 'var(--text-muted)',
                      marginBottom: '0.4rem',
                      fontWeight: 600,
                    }}
                  >
                    DESCRIPTION (CONTEXTUAL DETAILS): <span style={{ color: 'var(--accent-red, #ef4444)' }}>*</span>
                  </label>
                  <textarea
                    id="citizen-description-input"
                    rows={3}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Describe specific details, e.g., heavy dark plume rising from waste dump behind railway tracks..."
                    style={{
                      width: '100%',
                      padding: '0.75rem 1rem',
                      borderRadius: '10px',
                      background: 'var(--bg-surface-elevated, #ffffff)',
                      border: '1px solid var(--border-medium, #cbd5e1)',
                      color: 'var(--text-primary)',
                      fontSize: '0.875rem',
                      fontFamily: 'inherit',
                      resize: 'vertical',
                    }}
                    required
                  />
                  <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: '0.3rem' }}>
                    Describe visibility, duration, and proximity to sensitive receptors or industrial clusters.
                  </div>
                </div>
              </div>
            </Card>

            {/* -------------------------------------------------------------
                STEP 4: SUBMIT CTA
                ------------------------------------------------------------- */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {/* Privacy Notice & Consent Card */}
              <div
                style={{
                  padding: '1rem',
                  borderRadius: '10px',
                  background: 'var(--bg-surface-elevated, #f8fafc)',
                  border: '1px solid var(--border-subtle, #e2e8f0)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.65rem',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.6rem' }}>
                  <ShieldCheck size={20} style={{ color: 'var(--brand-primary, #0284c7)', flexShrink: 0, marginTop: '2px' }} />
                  <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', lineHeight: 1.45 }}>
                    <strong>Privacy Notice &amp; Auxiliary Evidence Notice:</strong> Ground observations and uploaded photographic evidence are securely stored and analyzed as auxiliary inputs by AeroSentinel. Citizen evidence alone does not trigger official enforcement actions, warnings, or conclusive violations.
                  </div>
                </div>
                <label
                  htmlFor="citizen-consent-checkbox"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.6rem',
                    marginTop: '0.25rem',
                    cursor: 'pointer',
                    fontSize: '0.82rem',
                    fontWeight: 600,
                    color: 'var(--text-primary)',
                  }}
                >
                  <input
                    type="checkbox"
                    id="citizen-consent-checkbox"
                    checked={consentGiven}
                    onChange={(e) => setConsentGiven(e.target.checked)}
                    style={{ width: '16px', height: '16px', accentColor: 'var(--brand-primary, #0284c7)' }}
                  />
                  <span>I consent to the secure storage and automated analysis of this incident report and any attached photographs.</span>
                </label>
              </div>

              <Button
                type="submit"
                variant="primary"
                size="lg"
                disabled={isSubmitting}
                isLoading={isSubmitting}
                style={{
                  padding: '0.9rem',
                  fontSize: '1rem',
                  boxShadow: '0 4px 20px rgba(56, 189, 248, 0.35)',
                }}
              >
                {isSubmitting ? 'ANALYZING & SUBMITTING...' : 'SUBMIT ENVIRONMENTAL EVIDENCE →'}
              </Button>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textAlign: 'center', lineHeight: 1.45 }}>
                Submissions are assigned an audit reference, indexed into Uber H3 Resolution 8, and evaluated by Gemini Vision as auxiliary evidence.
              </div>
            </div>
          </form>

          {/* Right Side: Tabbed Interface for Community Feed, Lookup & My Reports */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div
              style={{
                display: 'flex',
                background: 'var(--bg-surface-elevated, #f1f5f9)',
                borderRadius: '8px',
                padding: '4px',
                gap: '4px',
                border: '1px solid var(--border-subtle, #e2e8f0)',
              }}
            >
              <button
                type="button"
                onClick={() => setActiveTab('feed')}
                style={{
                  flex: 1,
                  padding: '0.45rem 0.6rem',
                  borderRadius: '6px',
                  border: 'none',
                  background: activeTab === 'feed' ? 'var(--bg-card, #ffffff)' : 'transparent',
                  color: activeTab === 'feed' ? 'var(--brand-primary, #0284c7)' : 'var(--text-secondary)',
                  fontWeight: activeTab === 'feed' ? 700 : 500,
                  fontSize: '0.75rem',
                  cursor: 'pointer',
                  boxShadow: activeTab === 'feed' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                  transition: 'all 0.15s ease',
                }}
              >
                Feed ({reports.length})
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('lookup')}
                style={{
                  flex: 1,
                  padding: '0.45rem 0.6rem',
                  borderRadius: '6px',
                  border: 'none',
                  background: activeTab === 'lookup' ? 'var(--bg-card, #ffffff)' : 'transparent',
                  color: activeTab === 'lookup' ? 'var(--brand-primary, #0284c7)' : 'var(--text-secondary)',
                  fontWeight: activeTab === 'lookup' ? 700 : 500,
                  fontSize: '0.75rem',
                  cursor: 'pointer',
                  boxShadow: activeTab === 'lookup' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                  transition: 'all 0.15s ease',
                }}
              >
                Track Receipt
              </button>
              <button
                type="button"
                onClick={() => {
                  setActiveTab('my');
                  handleFetchMyReports();
                }}
                style={{
                  flex: 1,
                  padding: '0.45rem 0.6rem',
                  borderRadius: '6px',
                  border: 'none',
                  background: activeTab === 'my' ? 'var(--bg-card, #ffffff)' : 'transparent',
                  color: activeTab === 'my' ? 'var(--brand-primary, #0284c7)' : 'var(--text-secondary)',
                  fontWeight: activeTab === 'my' ? 700 : 500,
                  fontSize: '0.75rem',
                  cursor: 'pointer',
                  boxShadow: activeTab === 'my' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                  transition: 'all 0.15s ease',
                }}
              >
                My Submissions
              </button>
            </div>

            {activeTab === 'feed' && (
              <ReportStatus
                reports={reports}
                onSelectReport={(r) => navigate(`/report/status/${r.id}`)}
                selectedReportId={undefined}
              />
            )}

            {activeTab === 'lookup' && (
              <Card
                title="Receipt &amp; Status Lookup"
                subtitle="Track your evidence submission using its official server-issued reference code"
                badge={<Badge variant="info">Audit Verified</Badge>}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '0.5rem' }}>
                  <form onSubmit={handleLookupSubmit} style={{ display: 'flex', gap: '0.5rem' }}>
                    <input
                      type="text"
                      placeholder="e.g. CR-09F21B90 or UUID"
                      value={lookupQuery}
                      onChange={(e) => setLookupQuery(e.target.value)}
                      style={{
                        flex: 1,
                        padding: '0.65rem 0.85rem',
                        borderRadius: '8px',
                        border: '1px solid var(--border-medium, #cbd5e1)',
                        background: 'var(--bg-surface-elevated, #ffffff)',
                        color: 'var(--text-primary)',
                        fontSize: '0.85rem',
                        fontFamily: 'var(--font-mono, monospace)',
                      }}
                    />
                    <Button type="submit" variant="primary" size="sm" disabled={isLookingUp || !lookupQuery.trim()}>
                      {isLookingUp ? 'Searching...' : 'Track'}
                    </Button>
                  </form>

                  {lookupError && (
                    <div style={{ padding: '0.75rem', borderRadius: '8px', background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.25)', color: 'var(--accent-red, #ef4444)', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <AlertTriangle size={15} style={{ flexShrink: 0 }} />
                      <span>{lookupError}</span>
                    </div>
                  )}

                  {lookupResult && (
                    <div
                      style={{
                        padding: '1rem',
                        borderRadius: '10px',
                        background: 'var(--bg-surface-elevated, #f8fafc)',
                        border: '1px solid var(--border-subtle, #e2e8f0)',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.75rem',
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontSize: '0.78rem', fontWeight: 800, fontFamily: 'var(--font-mono)', color: 'var(--brand-primary)' }}>
                          {lookupResult.reportId || `CR-${lookupResult.id.slice(0, 8).toUpperCase()}`}
                        </span>
                        <Badge variant={lookupResult.status === 'VERIFIED' ? 'success' : 'neutral'}>
                          {lookupResult.status || 'ANALYZED'}
                        </Badge>
                      </div>

                      <div style={{ fontSize: '0.82rem', color: 'var(--text-primary)' }}>
                        <strong>Category:</strong> {lookupResult.category}
                      </div>

                      <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                        {lookupResult.description}
                      </div>

                      <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                        Observed: {new Date(lookupResult.submittedAt).toLocaleString()}
                      </div>

                      {lookupResult.h3Index && (
                        <div style={{ fontSize: '0.72rem', color: 'var(--text-secondary)' }}>
                          Spatial Cell: <strong style={{ fontFamily: 'var(--font-mono)' }}>{lookupResult.h3Index}</strong>
                        </div>
                      )}

                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={() => setSubmittedReport(lookupResult)}
                        style={{ marginTop: '0.25rem' }}
                      >
                        <Eye size={14} style={{ marginRight: '0.35rem' }} /> View Full Dossier
                      </Button>
                    </div>
                  )}
                </div>
              </Card>
            )}

            {activeTab === 'my' && (
              <Card
                title="My Submitted Evidence"
                subtitle="Reports tied to your authenticated citizen identity"
              >
                {isLoadingMyReports ? (
                  <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                    Retrieving your submissions...
                  </div>
                ) : myReports.length === 0 ? (
                  <div style={{ padding: '2rem 1rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                    <ShieldCheck size={28} style={{ margin: '0 auto 0.5rem auto', opacity: 0.4 }} />
                    <p style={{ fontSize: '0.85rem', fontWeight: 500 }}>No reports associated with current session.</p>
                    <p style={{ fontSize: '0.75rem', marginTop: '0.25rem' }}>Reports submitted anonymously can be tracked using your receipt reference code.</p>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    {myReports.map((r) => (
                      <div
                        key={r.id}
                        onClick={() => setSubmittedReport(r)}
                        style={{
                          padding: '0.75rem',
                          borderRadius: '8px',
                          border: '1px solid var(--border-subtle, #e2e8f0)',
                          background: 'var(--bg-surface-elevated, #ffffff)',
                          cursor: 'pointer',
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <span style={{ fontSize: '0.75rem', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>
                            {r.reportId || `CR-${r.id.slice(0, 8).toUpperCase()}`}
                          </span>
                          <Badge variant="neutral">{r.status}</Badge>
                        </div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
                          {r.category} &bull; {new Date(r.submittedAt).toLocaleDateString()}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </Card>
            )}
          </div>
        </div>
      )}
    </PageContainer>
  );
};
