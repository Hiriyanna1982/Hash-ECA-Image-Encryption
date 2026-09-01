"""
Table 1 rule-pool generation pipeline.

Reproduces the intrinsic ECA rule-pool selection described in Section 4.3:
  Stage 1 - exhaustive screening of all 256 rules (balance, coverage,
            sensitivity, linearity); reject trivial + affine rules.
  Stage 2 - pairwise diversity and symmetry-class deduplication.
  Stage 3 - data-driven quality floor, then greedy maximin-complementarity
            nested pool construction (pools A through F).
  Stage 4 - intrinsic evaluation of each nested pool.

Input:  eca_outputs.csv (rule, seed, output for all 256x256 combinations;
        regenerate with ECAExhaustive.java if not present).
Output: pool_definitions.json, pool_evaluation.json (Table 1 contents).

Run:
    javac -d out ECAExhaustive.java CipherUtil.java DiffusionV2.java ...
    java -cp out white.ECAExhaustive eca_outputs.csv
    python3 table1_rule_pool_generation.py
"""
import numpy as np
import pandas as pd
import json

# ---------------------------------------------------------------------------
# Stage 1: per-rule metrics (balance, coverage, sensitivity, affine test)
# ---------------------------------------------------------------------------
df = pd.read_csv("eca_outputs.csv")
outputs = np.zeros((256, 256), dtype=np.uint8)
for row in df.itertuples(index=False):
    outputs[row.rule, row.seed] = row.output


def popcount(x):
    return bin(int(x)).count("1")


rows = []
for rule in range(256):
    out = outputs[rule]

    total_bits = 256 * 8
    ones = sum(popcount(v) for v in out)
    balance = ones / total_bits

    coverage = len(set(int(v) for v in out))

    flips, total_out_bits = 0, 0
    for bitpos in range(8):
        for seed in range(256):
            seed2 = seed ^ (1 << bitpos)
            flips += popcount(int(out[seed]) ^ int(out[seed2]))
            total_out_bits += 8
    sensitivity = flips / total_out_bits

    o0 = int(out[0])
    is_affine = True
    for a in range(256):
        oa = int(out[a])
        for b in range(256):
            if int(out[a ^ b]) != (oa ^ int(out[b]) ^ o0):
                is_affine = False
                break
        if not is_affine:
            break

    is_constant = coverage == 1
    is_identity = all(int(out[s]) == s for s in range(256))
    is_complement = all(int(out[s]) == (s ^ 0xFF) for s in range(256))

    rows.append(dict(rule=rule, balance=balance, coverage=coverage,
                      sensitivity=sensitivity, is_affine=is_affine,
                      is_constant=is_constant, is_identity=is_identity,
                      is_complement=is_complement))

metrics = pd.DataFrame(rows).set_index("rule")
metrics.to_csv("stage1_rule_metrics.csv")

TRIVIAL = {0, 51, 204, 255}
AFFINE = set(metrics[metrics.is_affine].index.tolist())
assert TRIVIAL.issubset(AFFINE) and len(AFFINE) == 16

# Nonlinear candidates = 256 - |AFFINE| (AFFINE already contains all 4 trivial
# rules; do not subtract TRIVIAL separately, that would double-count).
nonlinear_candidates = sorted(set(range(256)) - AFFINE)
assert len(nonlinear_candidates) == 240

# ---------------------------------------------------------------------------
# Stage 2/3: quality floor (data-driven), symmetry-class deduplication
# ---------------------------------------------------------------------------
med_cov = metrics.loc[nonlinear_candidates, "coverage"].median()
med_sens = metrics.loc[nonlinear_candidates, "sensitivity"].median()

qualified = [r for r in nonlinear_candidates
             if metrics.loc[r, "coverage"] >= med_cov
             and metrics.loc[r, "sensitivity"] >= med_sens]


def mirror_rule(rule):
    new_rule = 0
    for nbhd in range(8):
        left, center, right = (nbhd >> 2) & 1, (nbhd >> 1) & 1, nbhd & 1
        mirrored_nbhd = (right << 2) | (center << 1) | left
        bit = (rule >> mirrored_nbhd) & 1
        new_rule |= bit << nbhd
    return new_rule


def complement_rule(rule):
    new_rule = 0
    for nbhd in range(8):
        comp_nbhd = 7 - nbhd
        bit = 1 - ((rule >> comp_nbhd) & 1)
        new_rule |= bit << nbhd
    return new_rule


def mirror_complement_rule(rule):
    return complement_rule(mirror_rule(rule))


q_set = set(qualified)
seen, classes = set(), []
for r in qualified:
    if r in seen:
        continue
    cls = {r}
    for x in (mirror_rule(r), complement_rule(r), mirror_complement_rule(r)):
        if x in q_set:
            cls.add(x)
    classes.append(sorted(cls))
    seen |= cls

representatives = sorted(
    max(cls, key=lambda r: (metrics.loc[r, "sensitivity"],
                             metrics.loc[r, "coverage"],
                             -abs(metrics.loc[r, "balance"] - 0.5)))
    for cls in classes
)

# ---------------------------------------------------------------------------
# Stage 3 (continued): greedy maximin-complementarity nested pool construction
# ---------------------------------------------------------------------------
def norm_hamming(r1, r2):
    xor = np.bitwise_xor(outputs[r1], outputs[r2])
    return np.mean([popcount(v) for v in xor]) / 8.0


def complementarity(d):
    return 1 - 2 * abs(d - 0.5)


n = len(representatives)
D = np.zeros((n, n))
idx = {r: i for i, r in enumerate(representatives)}
for i in range(n):
    for j in range(i + 1, n):
        d = norm_hamming(representatives[i], representatives[j])
        D[i, j] = D[j, i] = d

best_pair, best_score = None, -1
for i in range(n):
    for j in range(i + 1, n):
        s = complementarity(D[i, j])
        cand = (representatives[i], representatives[j])
        if s > best_score:
            best_score, best_pair = s, cand
        elif s == best_score:
            def qual(pair):
                return (sum(metrics.loc[r, "sensitivity"] for r in pair)
                        + 0.0001 * sum(metrics.loc[r, "coverage"] for r in pair))
            if qual(cand) > qual(best_pair):
                best_pair = cand

pool = list(best_pair)
pool_sizes = {len(pool): list(pool)}
while len(pool) < 7:
    best_x, best_x_score = None, -1
    for r in representatives:
        if r in pool:
            continue
        worst = min(complementarity(D[idx[r], idx[p]]) for p in pool)
        if worst > best_x_score:
            best_x_score, best_x = worst, r
        elif worst == best_x_score:
            if best_x is None or (metrics.loc[r, "sensitivity"], metrics.loc[r, "coverage"]) > \
               (metrics.loc[best_x, "sensitivity"], metrics.loc[best_x, "coverage"]):
                best_x = r
    pool.append(best_x)
    pool_sizes[len(pool)] = list(pool)

with open("pool_definitions.json", "w") as f:
    json.dump(pool_sizes, f, indent=2)

# ---------------------------------------------------------------------------
# Stage 4: intrinsic evaluation of each nested pool
# ---------------------------------------------------------------------------
import itertools

pool_names = {2: "A", 3: "B", 4: "C", 5: "D", 6: "E", 7: "F"}
evaluation = {}
for size, rules in pool_sizes.items():
    pairs = list(itertools.combinations(rules, 2))
    dists = [norm_hamming(a, b) for a, b in pairs]
    mean_dist = float(np.mean(dists))
    mean_compl = float(np.mean([complementarity(d) for d in dists]))

    distinct_counts = []
    for seed in range(256):
        vals = [int(outputs[r, seed]) for r in rules]
        distinct_counts.append(len(set(vals)))
    avg_distinct = float(np.mean(distinct_counts))
    redundancy = float(np.mean([size - c for c in distinct_counts]) / (size - 1)) if size > 1 else 0.0

    evaluation[pool_names[size]] = dict(
        size=size, rules=rules,
        mean_pairwise_distance=mean_dist,
        mean_complementarity=mean_compl,
        avg_distinct_outputs_per_seed=avg_distinct,
        redundancy_rate=redundancy,
    )

with open("pool_evaluation.json", "w") as f:
    json.dump(evaluation, f, indent=2)

print("Pool definitions:", pool_sizes)
print()
for name, d in evaluation.items():
    print(f"{name}: rules={d['rules']} mean_complementarity={d['mean_complementarity']:.4f} "
          f"avg_distinct/seed={d['avg_distinct_outputs_per_seed']:.4f}")
