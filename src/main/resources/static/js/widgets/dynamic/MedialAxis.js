import D3Visualization from "../D3Visualization.js";

export default class MedialAxis extends D3Visualization {
  constructor(root, getData, {}) {
    super(root, getData, { top: 10, right: 10, bottom: 10, left: 10 });

    this.draw = {
      boundary: true,
      triangles: false,
      circles: false,
      centers: false,
      voronoi: false,
    };
  }

  async fetch() {
    return await fetch("/points.json").then((response) => response.json());
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    this.controls.appendSwitch("Boundary points", this.draw.boundary, () => {
      this.draw.boundary = !this.draw.boundary;
      this.render(this.data);
    });
    this.controls.appendSwitch(
      "Delaunay triangles",
      this.draw.triangles,
      () => {
        this.draw.triangles = !this.draw.triangles;
        this.render(this.data);
      },
    );
    this.controls.appendSwitch("Circumcircles", this.draw.circles, () => {
      this.draw.circles = !this.draw.circles;
      this.render(this.data);
    });
    this.controls.appendSwitch("Circumcenters", this.draw.centers, () => {
      this.draw.centers = !this.draw.centers;
      this.render(this.data);
    });
    this.controls.appendSwitch("Voronoi Edges", this.draw.voronoi, () => {
      this.draw.voronoi = !this.draw.voronoi;
      this.render(this.data);
    });
  }

  render(data) {
    this.clear();

    // Create the horizontal and vertical scales
    const xScale = d3
      .scaleLinear()
      .range([0, this.width])
      .domain(this.domain(data, (d) => d.x));

    const yScale = d3
      .scaleLinear()
      .range([0, this.height])
      .domain(this.domain(data, (d) => d.y));

    const points = data.map((d) => [xScale(d.x), yScale(d.y)]);
    const delaunay = d3.Delaunay.from(points);

    const { triangleCenters, radii } = this.calculateCircumcenters(
      points,
      delaunay.triangles,
    );
    const { voronoiEdges, medialEdges } = this.calculateVoronoi(
      points,
      delaunay.halfedges,
      triangleCenters,
    );

    // Draw delaunay triangles
    if (this.draw.triangles) {
      this.drawPath(delaunay.render());
    }

    // Draw voronoi edges
    if (this.draw.voronoi) {
      this.drawLines("voronoi", voronoiEdges, "red");
    }

    // Draw medial edges (axis)
    this.drawLines("medial", medialEdges, "black", 2);

    // Draw circumcircles
    if (this.draw.circles) {
      this.drawCircles(
        "circle",
        triangleCenters,
        "none",
        (_, i) => radii[i],
      ).attr("stroke", "steelblue");
    }

    // Draw circumcenters
    if (this.draw.centers) {
      this.drawCircles("center", triangleCenters, "blue");
    }

    // Draw boundary points
    if (this.draw.boundary) {
      this.drawCircles("boundary", points);
    }

    // Cache rendered data
    this.data = data;
  }

  domain(data, fn, padding = 0.05) {
    const [min, max] = d3.extent(data, fn);
    const range = max - min;

    return [min - range * padding, max + range * padding];
  }

  circumcenter(a, b, c) {
    // Unpack the triangle vertices
    const [ax, ay] = a;
    const [bx, by] = b;
    const [cx, cy] = c;

    // Compute the denominator
    const d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));

    if (d === 0) return null; // Vertices are collinear, no circumcenter exists

    // Calculate the x-coordinate of the circumcenter
    const ux =
      ((ax ** 2 + ay ** 2) * (by - cy) +
        (bx ** 2 + by ** 2) * (cy - ay) +
        (cx ** 2 + cy ** 2) * (ay - by)) /
      d;

    // Calculate the y-coordinate of the circumcenter
    const uy =
      ((ax ** 2 + ay ** 2) * (cx - bx) +
        (bx ** 2 + by ** 2) * (ax - cx) +
        (cx ** 2 + cy ** 2) * (bx - ax)) /
      d;

    return [ux, uy];
  }

  calculateCircumcenters(points, triangles) {
    const triangleCenters = [];
    const radii = [];

    for (let i = 0; i < triangles.length; i += 3) {
      const a = points[triangles[i]];
      const b = points[triangles[i + 1]];
      const c = points[triangles[i + 2]];

      const center = this.circumcenter(a, b, c);
      const radius = Math.hypot(center[0] - a[0], center[1] - a[1]);

      triangleCenters.push(center);
      radii.push(radius);
    }

    return { triangleCenters, radii };
  }

  calculateVoronoi(points, halfedges, triangleCenters) {
    const voronoiEdges = [];
    const medialEdges = [];

    for (let edge = 0; edge < halfedges.length; edge++) {
      const opposite = halfedges[edge];

      if (opposite < edge) continue; // avoid duplicates
      if (opposite === -1) continue; // skip edges on the convex hull

      // Get indexes of both triangles
      const t1 = Math.floor(edge / 3);
      const t2 = Math.floor(opposite / 3);

      // Get circumcenters of both triangles
      const c1 = triangleCenters[t1];
      const c2 = triangleCenters[t2];

      voronoiEdges.push([c1, c2]);

      // Add to medial axis if inside boundary
      if (d3.polygonContains(points, c1) && d3.polygonContains(points, c2)) {
        medialEdges.push([c1, c2]);
      }
    }

    return { voronoiEdges, medialEdges };
  }

  drawPath(path, color = "gray", width = 1) {
    this.svg
      .select("g")
      .append("path")
      .attr("fill", "none")
      .attr("stroke", color)
      .attr("stroke-width", width)
      .attr("d", path);
  }

  drawLines(key, edges, color = "gray", width = 1) {
    this.svg
      .select("g")
      .selectAll("line." + key)
      .data(edges)
      .join("line")
      .attr("x1", (d) => d[0][0])
      .attr("y1", (d) => d[0][1])
      .attr("x2", (d) => d[1][0])
      .attr("y2", (d) => d[1][1])
      .attr("stroke", color)
      .attr("stroke-width", width);
  }

  drawCircles(key, points, color = "red", radius = 2) {
    return this.svg
      .select("g")
      .selectAll("circle." + key)
      .data(points)
      .join("circle")
      .attr("cx", (d) => d[0])
      .attr("cy", (d) => d[1])
      .attr("r", radius)
      .attr("fill", color);
  }
}
