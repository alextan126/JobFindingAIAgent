import math
import re
from collections import Counter
from typing import Dict, List, Tuple

# --- simple tokenizer ---
_TOKEN = re.compile(r"[A-Za-z0-9_]+")

def tokenize(text: str) -> List[str]:
    return [t.lower() for t in _TOKEN.findall(text or "")]

def tfidf_vector(tokens: List[str], idf: Dict[str, float]) -> Dict[str, float]:
    tf = Counter(tokens)  # raw term frequency
    return {w: tf[w] * idf.get(w, 0.0) for w in tf}

def cosine_sparse(a: Dict[str, float], b: Dict[str, float]) -> float:
    if not a or not b:
        return 0.0
    dot = sum(a[k] * b[k] for k in a.keys() & b.keys())
    na = math.sqrt(sum(v*v for v in a.values()))
    nb = math.sqrt(sum(v*v for v in b.values()))
    return 0.0 if na == 0 or nb == 0 else dot / (na * nb)

def local_idf(two_docs_tokens: List[List[str]]) -> Dict[str, float]:
    """
    IDF computed only from the two docs: user_jd and job_jd.
    Smoothed: ln((N + 1) / (df + 1)) + 1 with N = 2.
    """
    N = 2
    df = Counter()
    vocab = set()
    for toks in two_docs_tokens:
        s = set(toks)
        df.update(s)
        vocab |= s
    return {w: math.log((N + 1) / (df[w] + 1)) + 1.0 for w in vocab}

def top_overlap_terms(a: Dict[str, float], b: Dict[str, float], k: int = 15) -> List[Tuple[str, float]]:
    common = [(w, a[w] * b[w]) for w in (a.keys() & b.keys())]
    common.sort(key=lambda x: x[1], reverse=True)
    return common[:k]