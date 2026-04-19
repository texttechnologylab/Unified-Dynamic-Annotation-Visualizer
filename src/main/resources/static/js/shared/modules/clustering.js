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
        f.push(found);
        clusterPoints.push(f);
      }
    }
  }

  return clusterPoints;
}

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
    p.push(found || p);
    clusterPoints.push(p);
  });

  return clusterPoints;
}
