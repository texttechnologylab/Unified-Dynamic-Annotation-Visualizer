// https://d3js.org/d3-quadtree
export function quadtreeClustering(points, rows, cols, cellSize) {
  const tree = d3.quadtree(points);
  const clusterPoints = [];

  function searchInTree(xmin, ymin, xmax, ymax) {
    const results = [];

    tree.visit((node, x1, y1, x2, y2) => {
      if (!node.length) {
        do {
          let d = node.data;
          if (d[0] >= xmin && d[0] < xmax && d[1] >= ymin && d[1] < ymax) {
            results.push(d);
          }
        } while ((node = node.next));
      }
      return x1 >= xmax || y1 >= ymax || x2 < xmin || y2 < ymin;
    });

    return results;
  }

  for (let y = 0; y < rows; y++) {
    for (let x = 0; x < cols; x++) {
      const found = searchInTree(
        x * cellSize,
        y * cellSize,
        x * cellSize + cellSize,
        y * cellSize + cellSize,
      );

      for (const f of found) {
        f.push(found.length);
        clusterPoints.push(f);
      }
    }
  }

  return clusterPoints;
}

// https://dl.acm.org/doi/10.5555/3001460.3001507
export function dbscanClustering(data, points, epsilon, minPts) {
  function euclideanDist(a, b) {
    const dx = a[0] - b[0];
    const dy = a[1] - b[1];
    return Math.sqrt(dx * dx + dy * dy);
  }

  function rangeQuery(idx, epsilon) {
    return points.reduce((neighbors, point, i) => {
      if (euclideanDist(points[idx], point) <= epsilon) neighbors.push(i);
      return neighbors;
    }, []);
  }

  function dbscan(points, epsilon, minPts) {
    const labels = new Array(points.length).fill(undefined);
    let clusterId = 0;

    for (let i = 0; i < points.length; i++) {
      if (labels[i] !== undefined) continue;

      const neighbors = rangeQuery(i, epsilon);

      if (neighbors.length < minPts) {
        labels[i] = -1; // noise
        continue;
      }

      labels[i] = clusterId;

      const seeds = neighbors.filter((n) => n !== i);

      for (let j = 0; j < seeds.length; j++) {
        const s = seeds[j];

        if (labels[s] === -1) labels[s] = clusterId;
        if (labels[s] !== undefined) continue;

        labels[s] = clusterId;

        const newNeighbors = rangeQuery(s, epsilon);
        if (newNeighbors.length >= minPts) {
          seeds.push(
            ...newNeighbors.filter((n) => !seeds.includes(n) && n !== s),
          );
        }
      }

      clusterId++;
    }

    return labels;
  }

  const labels = dbscan(points, epsilon, minPts);
  const groups = d3.group(data, (_, i) => labels[i]);
  groups.delete(-1);

  const clusterPoints = [];

  points.forEach((p, i) => {
    const found = groups.get(labels[i]);
    p.push(found ? found.length : 1);
    clusterPoints.push(p);
  });

  return clusterPoints;
}

// https://github.com/d3/d3-delaunay
export function voronoiClustering(points, width, height) {
  if (points.length < 2) {
    return points.map((p) => [p[0], p[1], 1]);
  }

  const delaunay = d3.Delaunay.from(points);
  const voronoi = delaunay.voronoi([0, 0, width, height]);

  // Collect all cell areas first so we can normalise
  const areas = points.map((_, i) => {
    const poly = voronoi.cellPolygon(i);
    return poly ? Math.abs(d3.polygonArea(poly)) : Infinity;
  });

  const maxArea = d3.max(areas.filter(isFinite));

  return points.map((p, i) => {
    const area = areas[i];
    // Small area = dense neighbourhood → large radius
    // Clamp edge cells (Infinity) to a radius of 1
    const radius = isFinite(area) ? (1 - area / maxArea) * 10 + 1 : 1;
    return [p[0], p[1], radius];
  });
}

// k-th nearest neighbour distance as density proxy
export function knnClustering(points, k = 5) {
  if (points.length <= k) {
    return points.map((p) => [p[0], p[1], 1]);
  }

  // For each point, find the distance to its k-th nearest neighbour
  const kDistances = points.map((p, i) => {
    const sorted = points
      .map((q, j) => ({ j, dist: Math.hypot(p[0] - q[0], p[1] - q[1]) }))
      .filter(({ j }) => j !== i) // exclude self
      .sort((a, b) => a.dist - b.dist);

    return sorted[k - 1]?.dist ?? Infinity;
  });

  const maxDist = d3.max(kDistances.filter(isFinite));

  return points.map((p, i) => {
    const d = kDistances[i];
    // Short distance = dense area → large radius
    const radius = isFinite(d) ? (1 - d / maxDist) * 10 + 1 : 1;
    return [p[0], p[1], radius];
  });
}

// https://github.com/d3/d3-delaunay
export function delaunayClustering(points) {
  if (points.length < 2) {
    return points.map((p) => [p[0], p[1], 1]);
  }

  const delaunay = d3.Delaunay.from(points);

  // Build adjacency: for each point collect the lengths of its Delaunay edges
  const edgeLengths = points.map(() => []);

  // delaunay.triangles is a flat array [i0, i1, i2,  i3, i4, i5, ...]
  for (let t = 0; t < delaunay.triangles.length; t += 3) {
    const a = delaunay.triangles[t];
    const b = delaunay.triangles[t + 1];
    const c = delaunay.triangles[t + 2];

    const pairs = [
      [a, b],
      [b, c],
      [a, c],
    ];
    for (const [i, j] of pairs) {
      const len = Math.hypot(
        points[i][0] - points[j][0],
        points[i][1] - points[j][1],
      );
      edgeLengths[i].push(len);
      edgeLengths[j].push(len);
    }
  }

  // Average edge length per point; isolated points fall back to Infinity
  const avgLengths = edgeLengths.map((lens) =>
    lens.length ? lens.reduce((s, l) => s + l, 0) / lens.length : Infinity,
  );

  const maxLen = d3.max(avgLengths.filter(isFinite));

  return points.map((p, i) => {
    const avg = avgLengths[i];
    // Short edge = dense → large radius
    const radius = isFinite(avg) ? (1 - avg / maxLen) * 10 + 1 : 1;
    return [p[0], p[1], radius];
  });
}

// Gaussian kernel density estimation evaluated at each point
export function gaussianKdeClustering(points, bandwidth = 30) {
  if (points.length < 2) {
    return points.map((p) => [p[0], p[1], 1]);
  }

  const h2 = bandwidth * bandwidth;

  // KDE value at point i = sum of Gaussian contributions from all other points
  const densities = points.map((p, i) => {
    let sum = 0;
    for (let j = 0; j < points.length; j++) {
      if (i === j) continue;
      const distSq = (p[0] - points[j][0]) ** 2 + (p[1] - points[j][1]) ** 2;
      sum += Math.exp(-distSq / (2 * h2));
    }
    return sum; // proportional to density, normalisation not needed here
  });

  const maxDensity = d3.max(densities);

  return points.map((p, i) => {
    const radius = (densities[i] / maxDensity) * 10 + 1;
    return [p[0], p[1], radius];
  });
}
