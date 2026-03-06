import D3Visualization from "../D3Visualization.js";
import { getGeneratorOptions } from "../../pages/editor/utils/editorActions.js";

export default class MedialAxis extends D3Visualization {
  static defaultConfig = {
    type: "MedialAxis",
    title: "Medial Axis",
    generator: { id: "" },
    options: {},
    icon: "bi bi-slash-square",
    w: 8,
    h: 6,
  };
  static formConfig = {
    title: {
      type: "text",
      label: "Title",
    },
    "generator.id": {
      type: "select",
      label: "Generator",
      options: () => getGeneratorOptions("MapCoordinates"),
    },
  };
  static previewData = [
    { x: 10, y: 10 },
    { x: 12, y: 10 },
    { x: 14, y: 10 },
    { x: 16, y: 10 },
    { x: 18, y: 10 },
    { x: 20, y: 10 },
    { x: 22, y: 10 },
    { x: 24, y: 10 },
    { x: 26, y: 10 },
    { x: 28, y: 10 },
    { x: 30, y: 10 },
    { x: 32, y: 10 },
    { x: 34, y: 10 },
    { x: 36, y: 10 },
    { x: 38, y: 10 },
    { x: 40, y: 10 },
    { x: 40, y: 12 },
    { x: 40, y: 14 },
    { x: 40, y: 16 },
    { x: 40, y: 18 },
    { x: 40, y: 20 },
    { x: 38, y: 20 },
    { x: 36, y: 20 },
    { x: 34, y: 20 },
    { x: 32, y: 20 },
    { x: 30, y: 20 },
    { x: 28, y: 20 },
    { x: 26, y: 20 },
    { x: 24, y: 20 },
    { x: 22, y: 20 },
    { x: 20, y: 20 },
    { x: 20, y: 22 },
    { x: 20, y: 24 },
    { x: 20, y: 26 },
    { x: 20, y: 28 },
    { x: 20, y: 30 },
    { x: 20, y: 32 },
    { x: 20, y: 34 },
    { x: 20, y: 36 },
    { x: 20, y: 38 },
    { x: 20, y: 40 },
    { x: 18, y: 40 },
    { x: 16, y: 40 },
    { x: 14, y: 40 },
    { x: 12, y: 40 },
    { x: 10, y: 40 },
    { x: 10, y: 12 },
    { x: 10, y: 14 },
    { x: 10, y: 16 },
    { x: 10, y: 18 },
    { x: 10, y: 38 },
    { x: 10, y: 36 },
    { x: 10, y: 34 },
    { x: 10, y: 32 },
    { x: 10, y: 30 },
    { x: 10, y: 28 },
    { x: 10, y: 26 },
    { x: 10, y: 24 },
    { x: 10, y: 22 },
    { x: 10, y: 20 },
  ];

  constructor(root, config) {
    super(root, config, { top: 40, right: 40, bottom: 40, left: 40 });

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

    this.controls.append([
      {
        type: "switch",
        label: "Boundary points",
        value: this.draw.boundary,
        onchange: () => {
          this.draw.boundary = !this.draw.boundary;
          this.render(this.data);
        },
      },
      {
        type: "switch",
        label: "Delaunay triangles",
        value: this.draw.triangles,
        onchange: () => {
          this.draw.triangles = !this.draw.triangles;
          this.render(this.data);
        },
      },
      {
        type: "switch",
        label: "Circumcircles",
        value: this.draw.circles,
        onchange: () => {
          this.draw.circles = !this.draw.circles;
          this.render(this.data);
        },
      },
      {
        type: "switch",
        label: "Circumcenters",
        value: this.draw.centers,
        onchange: () => {
          this.draw.centers = !this.draw.centers;
          this.render(this.data);
        },
      },
      {
        type: "switch",
        label: "Voronoi Edges",
        value: this.draw.voronoi,
        onchange: () => {
          this.draw.voronoi = !this.draw.voronoi;
          this.render(this.data);
        },
      },
    ]);
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
      .range([this.height, 0])
      .domain(this.domain(data, (d) => d.y));

    const { area, zoom } = this.createAxisZoom([1, 40], {
      bottom: xScale,
      left: yScale,
      top: xScale,
      right: yScale,
    });
    this.plotArea = area;

    const points = data.map((d) => [xScale(d.x), yScale(d.y)]);
    const delaunay = d3.Delaunay.from(points);

    const triangleCenters = this.calculateCircumcenters(
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
        "transparent",
        (d) => d.radius,
        "steelblue",
      ).attr("opacity", 0.4);
    }

    // Draw circumcenters
    if (this.draw.centers) {
      this.drawCircles("center", triangleCenters, "blue");
    }

    // Draw boundary points
    if (this.draw.boundary) {
      this.drawCircles("boundary", points);
    }

    if (!this.tooltip.empty()) {
      this.svg.call(zoom);

      // Draw invisible hover targets
      const container = this.plotArea.append("g");

      this.drawLines("hover", medialEdges, "transparent", 20)
        .style("cursor", "help")
        .on("mouseenter", (_, endpoints) => {
          const circles = this.buildHoverCircles(endpoints);

          container
            .selectAll("circle")
            .data(circles)
            .join("circle")
            .attr("cx", (d) => d.x)
            .attr("cy", (d) => d.y)
            .attr("r", (d) => d.r || 3)
            .attr("fill", (d) => (d.r ? "none" : d.color))
            .attr("stroke", (d) => (d.r ? d.color : "none"))
            .attr("stroke-width", 1.5);
        })
        .on("mouseleave", () => {
          container.selectAll("*").remove();
        });
    }

    // Cache rendered data
    this.data = data;
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

    for (let i = 0; i < triangles.length; i += 3) {
      const a = points[triangles[i]];
      const b = points[triangles[i + 1]];
      const c = points[triangles[i + 2]];

      const center = this.circumcenter(a, b, c);
      const radius = Math.hypot(center[0] - a[0], center[1] - a[1]);

      triangleCenters.push({ center, radius, vertices: [a, b, c] });
    }

    return triangleCenters;
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
      if (
        d3.polygonContains(points, c1.center) &&
        d3.polygonContains(points, c2.center)
      ) {
        medialEdges.push([c1, c2]);
      }
    }

    return { voronoiEdges, medialEdges };
  }

  buildHoverCircles(points) {
    const circles = [];

    for (const point of points) {
      // circumcenter
      circles.push({
        x: point.center[0],
        y: point.center[1],
        color: "magenta",
      });

      // circumcircle
      circles.push({
        x: point.center[0],
        y: point.center[1],
        r: point.radius,
        color: "teal",
      });

      // triangle vertices
      for (const vertice of point.vertices) {
        circles.push({
          x: vertice[0],
          y: vertice[1],
          color: "lime",
        });
      }
    }

    return circles;
  }

  drawPath(path, color = "gray", width = 1) {
    return this.plotArea
      .append("path")
      .attr("fill", "none")
      .attr("stroke", color)
      .attr("stroke-width", width)
      .attr("d", path);
  }

  drawLines(key, edges, color = "gray", width = 1) {
    return this.plotArea
      .selectAll("line." + key)
      .data(edges)
      .join("line")
      .attr("class", key)
      .attr("x1", (d) => d[0].center?.[0] || d[0][0])
      .attr("y1", (d) => d[0].center?.[1] || d[0][1])
      .attr("x2", (d) => d[1].center?.[0] || d[1][0])
      .attr("y2", (d) => d[1].center?.[1] || d[1][1])
      .attr("stroke", color)
      .attr("stroke-width", width);
  }

  drawCircles(key, points, fill = "red", radius = 2, stroke = "none") {
    return this.plotArea
      .selectAll("circle." + key)
      .data(points)
      .join("circle")
      .attr("class", key)
      .attr("cx", (d) => d.center?.[0] || d[0])
      .attr("cy", (d) => d.center?.[1] || d[1])
      .attr("r", radius)
      .attr("fill", fill)
      .attr("stroke", stroke);
  }
}
