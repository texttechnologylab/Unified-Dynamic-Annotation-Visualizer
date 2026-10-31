// Value range of widgets whose "Range" slider filters by value (BarChart, PieChart).
// The upper bound belongs to the page: an untouched slider follows the maximum of the page
// that is shown, so nothing is dropped silently when the user pages or exports all pages.

export function initPageRange(widget, data) {
  const max = d3.max(data.map((d) => d.value)) ?? 0;
  widget.pageMax = max;
  widget.range = [0, max];
  return max;
}

export async function rerenderWithPageRange(widget) {
  const untouched = widget.filter.max >= widget.pageMax;
  if (untouched) delete widget.filter.max;

  let result;
  try {
    result = await widget.fetch();
  } catch {
    widget.filter.max = widget.pageMax;
    return;
  }

  const data = result.data[0];
  const max = d3.max(data.map((d) => d.value)) ?? 0;
  widget.pageMax = max;
  if (untouched) {
    widget.filter.max = max;
    setRangeMax(widget, max);
  }
  widget.render(data);
}

function setRangeMax(widget, max) {
  widget.range[1] = max;
  const controls = widget.controls.controls;
  if (!controls) return;

  // the first slider of the pair is the upper one
  const sliders = controls.querySelectorAll("input.dv-slider-double");
  sliders.forEach((slider) => (slider.max = max));
  if (sliders[0]) sliders[0].value = max;

  const output = controls.querySelector("output");
  if (output) output.textContent = `${widget.range[0]} - ${max}`;
}
