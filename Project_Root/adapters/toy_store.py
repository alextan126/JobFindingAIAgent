def toy_record_submission(job_id: str, payload: dict):
    return {"ok": True, "job_id": job_id, "stored_payload_keys": list(payload.keys())}