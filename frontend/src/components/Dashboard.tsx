import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getUser, getJobMatches, refreshJobMatches, createApplication, User, JobMatch } from '../services/api';

const Dashboard: React.FC = () => {
  const [user, setUser] = useState<User | null>(null);
  const [matches, setMatches] = useState<JobMatch[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [applyingTo, setApplyingTo] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const navigate = useNavigate();
  const userEmail = localStorage.getItem('userEmail');

  useEffect(() => {
    if (!userEmail) {
      navigate('/login');
      return;
    }
    loadData();
  }, [userEmail, navigate]);

  const loadData = async () => {
    if (!userEmail) return;

    try {
      const [userData, matchesData] = await Promise.all([
        getUser(userEmail),
        getJobMatches(userEmail, 50),
      ]);
      setUser(userData);
      setMatches(matchesData);
    } catch (err: any) {
      setError('Failed to load data. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleRefreshMatches = async () => {
    if (!userEmail) return;
    setRefreshing(true);
    setError('');
    setSuccessMessage('');

    try {
      const result = await refreshJobMatches(userEmail);
      setSuccessMessage(`Found ${result.matches_found} new job matches!`);
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to refresh matches.');
    } finally {
      setRefreshing(false);
    }
  };

  const handleApply = async (jobInfoId: number) => {
    if (!userEmail) return;
    setApplyingTo(jobInfoId);
    setError('');
    setSuccessMessage('');

    try {
      const result = await createApplication(userEmail, jobInfoId);
      setSuccessMessage('Application created! AI-generated cover letter is ready.');
      // Reload to update application status
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to create application.');
    } finally {
      setApplyingTo(null);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('userEmail');
    navigate('/login');
  };

  if (loading) {
    return (
      <div style={styles.container}>
        <div style={styles.loading}>Loading your dashboard...</div>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <div>
          <h1 style={styles.title}>Job Matches Dashboard</h1>
          <p style={styles.subtitle}>Welcome back, {user?.full_name || userEmail}!</p>
        </div>
        <div style={styles.headerActions}>
          <button onClick={() => navigate('/upload')} style={styles.uploadButton}>
            Update Resume
          </button>
          <button onClick={handleLogout} style={styles.logoutButton}>
            Logout
          </button>
        </div>
      </div>

      {error && <div style={styles.error}>{error}</div>}
      {successMessage && <div style={styles.success}>{successMessage}</div>}

      <div style={styles.statsBar}>
        <div style={styles.statCard}>
          <div style={styles.statValue}>{matches.length}</div>
          <div style={styles.statLabel}>Job Matches</div>
        </div>
        <div style={styles.statCard}>
          <div style={styles.statValue}>
            {user?.skills && typeof user.skills === 'string'
              ? JSON.parse(user.skills).length
              : 0}
          </div>
          <div style={styles.statLabel}>Skills Identified</div>
        </div>
        <button
          onClick={handleRefreshMatches}
          disabled={refreshing}
          style={styles.refreshButton}
        >
          {refreshing ? 'Refreshing...' : 'Refresh Matches'}
        </button>
      </div>

      {user && !user.resume_text && (
        <div style={styles.warning}>
          You haven't uploaded a resume yet.{' '}
          <span onClick={() => navigate('/upload')} style={styles.warningLink}>
            Upload now
          </span>{' '}
          to get better job matches!
        </div>
      )}

      {user && user.skills && typeof user.skills === 'string' && JSON.parse(user.skills).length > 0 && (
        <div style={styles.skillsSection}>
          <h2 style={styles.skillsTitle}>Your Skills</h2>
          <div style={styles.skillsList}>
            {JSON.parse(user.skills as string).map((skill: string, idx: number) => (
              <span key={idx} style={styles.skillBadge}>
                {skill}
              </span>
            ))}
          </div>
        </div>
      )}

      <div style={styles.matchesList}>
        {matches.length === 0 ? (
          <div style={styles.emptyState}>
            <h3>No job matches yet</h3>
            <p>Upload your resume and click "Refresh Matches" to find jobs tailored to your skills.</p>
          </div>
        ) : (
          matches.map((match) => (
            <div key={match.job_id} style={styles.matchCard}>
              <div style={styles.matchHeader}>
                <div>
                  <h3 style={styles.matchTitle}>{match.title}</h3>
                  <p style={styles.matchCompany}>{match.company}</p>
                </div>
                <div style={styles.matchScore}>
                  {Math.round(match.match_score * 100)}% Match
                </div>
              </div>

              {match.location && (
                <p style={styles.matchDetail}>📍 {match.location}</p>
              )}
              {match.salary_range && (
                <p style={styles.matchDetail}>💰 {match.salary_range}</p>
              )}
              {match.job_type && (
                <p style={styles.matchDetail}>💼 {match.job_type}</p>
              )}

              <div style={styles.matchReason}>
                <strong>Why this match:</strong> {match.match_reason}
              </div>

              {match.matched_skills.length > 0 && (
                <div style={styles.skillsContainer}>
                  <strong>Matched Skills:</strong>
                  <div style={styles.skills}>
                    {match.matched_skills.map((skill, idx) => (
                      <span key={idx} style={styles.skillTag}>
                        {skill}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              <div style={styles.matchActions}>
                <a
                  href={match.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={styles.viewButton}
                >
                  View Job
                </a>
                <button
                  onClick={() => handleApply(match.job_id)}
                  disabled={applyingTo === match.job_id}
                  style={styles.applyButton}
                >
                  {applyingTo === match.job_id ? 'Generating...' : 'Apply with AI'}
                </button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    minHeight: '100vh',
    backgroundColor: '#f5f5f5',
    padding: '20px',
  },
  loading: {
    textAlign: 'center',
    padding: '40px',
    fontSize: '18px',
    color: '#666',
  },
  header: {
    backgroundColor: 'white',
    borderRadius: '8px',
    padding: '30px',
    marginBottom: '20px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: '20px',
  },
  title: {
    fontSize: '28px',
    fontWeight: 'bold',
    marginBottom: '8px',
  },
  subtitle: {
    fontSize: '16px',
    color: '#666',
  },
  headerActions: {
    display: 'flex',
    gap: '10px',
  },
  uploadButton: {
    padding: '10px 20px',
    backgroundColor: '#4CAF50',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: '500',
  },
  logoutButton: {
    padding: '10px 20px',
    backgroundColor: '#f44336',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: '500',
  },
  error: {
    backgroundColor: '#fee',
    border: '1px solid #fcc',
    color: '#c33',
    padding: '12px',
    borderRadius: '4px',
    marginBottom: '16px',
  },
  success: {
    backgroundColor: '#efe',
    border: '1px solid #cfc',
    color: '#3c3',
    padding: '12px',
    borderRadius: '4px',
    marginBottom: '16px',
  },
  warning: {
    backgroundColor: '#fff3cd',
    border: '1px solid #ffeaa7',
    color: '#856404',
    padding: '12px',
    borderRadius: '4px',
    marginBottom: '16px',
  },
  warningLink: {
    color: '#4CAF50',
    cursor: 'pointer',
    fontWeight: '500',
    textDecoration: 'underline',
  },
  statsBar: {
    backgroundColor: 'white',
    borderRadius: '8px',
    padding: '20px',
    marginBottom: '20px',
    display: 'flex',
    gap: '20px',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  statCard: {
    textAlign: 'center',
    padding: '10px',
  },
  statValue: {
    fontSize: '32px',
    fontWeight: 'bold',
    color: '#4CAF50',
  },
  statLabel: {
    fontSize: '14px',
    color: '#666',
    marginTop: '4px',
  },
  refreshButton: {
    padding: '10px 20px',
    backgroundColor: '#2196F3',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: '500',
    marginLeft: 'auto',
  },
  matchesList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  emptyState: {
    backgroundColor: 'white',
    borderRadius: '8px',
    padding: '60px',
    textAlign: 'center',
    color: '#666',
  },
  matchCard: {
    backgroundColor: 'white',
    borderRadius: '8px',
    padding: '24px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
  },
  matchHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'start',
    marginBottom: '12px',
  },
  matchTitle: {
    fontSize: '20px',
    fontWeight: 'bold',
    marginBottom: '4px',
  },
  matchCompany: {
    fontSize: '16px',
    color: '#666',
  },
  matchScore: {
    backgroundColor: '#4CAF50',
    color: 'white',
    padding: '8px 16px',
    borderRadius: '20px',
    fontSize: '14px',
    fontWeight: '600',
  },
  matchDetail: {
    fontSize: '14px',
    color: '#666',
    marginBottom: '8px',
  },
  matchReason: {
    fontSize: '14px',
    color: '#333',
    marginTop: '12px',
    padding: '12px',
    backgroundColor: '#f9f9f9',
    borderRadius: '4px',
    lineHeight: '1.5',
  },
  skillsContainer: {
    marginTop: '12px',
  },
  skills: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '8px',
    marginTop: '8px',
  },
  skillTag: {
    backgroundColor: '#e3f2fd',
    color: '#1976d2',
    padding: '4px 12px',
    borderRadius: '12px',
    fontSize: '13px',
  },
  matchActions: {
    display: 'flex',
    gap: '10px',
    marginTop: '16px',
  },
  viewButton: {
    padding: '10px 20px',
    backgroundColor: '#f5f5f5',
    color: '#333',
    border: '1px solid #ddd',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
    textDecoration: 'none',
    textAlign: 'center',
  },
  applyButton: {
    padding: '10px 20px',
    backgroundColor: '#4CAF50',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: '500',
  },
  skillsSection: {
    backgroundColor: 'white',
    borderRadius: '8px',
    padding: '24px',
    marginBottom: '20px',
  },
  skillsTitle: {
    fontSize: '20px',
    fontWeight: 'bold',
    marginBottom: '16px',
  },
  skillsList: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '10px',
  },
  skillBadge: {
    backgroundColor: '#4CAF50',
    color: 'white',
    padding: '8px 16px',
    borderRadius: '20px',
    fontSize: '14px',
    fontWeight: '500',
  },
};

export default Dashboard;
