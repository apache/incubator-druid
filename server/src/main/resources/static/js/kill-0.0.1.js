$(document).ready(function() {
  $("button").button();

  $("#error_dialog").dialog({
      autoOpen: false,
      modal:true,
      resizeable: false,
      buttons: {
        Ok : function() {
          $(this).dialog("close");
        }
      }
  });

  $("#confirm_dialog").dialog({
      autoOpen: false,
      modal:true,
      resizeable: false,
      buttons: {
        Yes : function() {
          var selected = $('#datasources option:selected').text();
          var interval = $('#interval').val();
          var toSend = {
            "dataSource" : selected,
            "interval" : interval
          }
          $.ajax({
            type: 'POST',
            url:'/master/kill',
            data: JSON.stringify(toSend),
            contentType:"application/json; charset=utf-8",
            dataType:"json",
            error: function(xhr, status, error) {
              $("#confirm_dialog").dialog("close");
              $("#error_dialog").html(xhr.responseText);
              $("#error_dialog").dialog("open");
            },
            success: function(data, status, xhr) {
              $("#confirm_dialog").dialog("close");
            }
          });
        },
        Cancel: function() {
          $(this).dialog("close");
        }
      }
  });

  $.getJSON("/info/db/datasources?includeDisabled", function(data) {
    $.each(data, function(index, datasource) {
      $('#datasources').append($('<option></option>').attr("value", datasource).text(datasource));
    });
  });

  $("#confirm").click(function() {
    $("#confirm_dialog").dialog("open");
  });
});