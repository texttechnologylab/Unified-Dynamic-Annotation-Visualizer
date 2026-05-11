| Method                    | Signal                            | Hyperparameters       | Smoothness  | Edge sensitivity     | O complexity |
| ------------------------- | --------------------------------- | --------------------- | ----------- | -------------------- | ------------ |
| Quadtree                  | Count of points in grid cell      | Cell size             | Blocky      | Low                  | O(n log n)   |
| DBSCAN                    | Cluster membership size           | ε, minPts             | Binary      | Low                  | O(n²)        |
| Voronoi area              | Inverse Voronoi cell area         | None                  | Moderate    | High (clipped cells) | O(n log n)   |
| kNN distance              | Inverse k-th neighbour distance   | k                     | Moderate    | Low                  | O(n²)        |
| Delaunay edge length      | Average Delaunay edge length      | None                  | Smooth      | Low                  | O(n log n)   |
| Gaussian KDE              | Gaussian kernel sum at each point | Bandwidth             | Very smooth | Low                  | O(n²)        |
| Kernel Density Estimation | KDE over full canvas grid         | Bandwidth, thresholds | Very smooth | None                 | O(n·w·h)     |
