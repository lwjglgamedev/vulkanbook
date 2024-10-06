function to_lowercase(str)
  return string.lower(str)
end

function is_next_chapter_sequence(content)
  local chapter_text = {"next", "chapter"}
  local index = 1

  for _, element in ipairs(content) do
    if element.t == "Str" then
      if to_lowercase(element.text) == chapter_text[index] then
        index = index + 1
        if index > #chapter_text then
          return true  -- Found "Next chapter"
        end
      end
    end
  end

  return false
end

-- Function to remove "Next Chapter" links
function Link(el)
  if is_next_chapter_sequence(el.content) then
    return {}  -- Remove the entire link
  else
    return el  -- Keep the link if it doesn't match
  end
end